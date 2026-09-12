package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.service.dto.TrackPageImageDTOs;
import com.fundaro.zodiac.taurus.utils.pdf.PageEditRecipe;
import com.fundaro.zodiac.taurus.utils.pdf.PageImageTransformer;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ImageTransformationService implements PageImageTransformer {

    static final long MAX_PIXELS = 40_000_000L;
    static final int MAX_SIDE = 20_000;
    static final int MAX_BYTES = 50 * 1024 * 1024;

    public TrackPageImageDTOs.Analysis analyze(Long mediaId, byte[] content) {
        BufferedImage image = decode(content);
        int width = image.getWidth();
        int height = image.getHeight();
        long pixels = (long) width * height;
        int step = Math.max(1, (int) Math.sqrt(pixels / 1_000_000d));
        long samples = 0;
        double sum = 0;
        double sumSquares = 0;
        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;

        for (int y = 0; y < height; y += step) {
            for (int x = 0; x < width; x += step) {
                int luminance = luminance(image.getRGB(x, y));
                sum += luminance;
                sumSquares += (double) luminance * luminance;
                samples++;
                if (luminance < 245) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }

        List<String> warnings = new ArrayList<>();
        TrackPageImageDTOs.Crop bounds;
        double borderRatio;
        if (maxX < minX || maxY < minY) {
            bounds = new TrackPageImageDTOs.Crop(0, 0, 1, 1);
            borderRatio = 0;
            warnings.add("PAGE_NEARLY_BLANK");
        } else {
            int paddingX = Math.max(1, width / 100);
            int paddingY = Math.max(1, height / 100);
            int left = Math.max(0, minX - paddingX);
            int top = Math.max(0, minY - paddingY);
            int right = Math.min(width, maxX + paddingX + 1);
            int bottom = Math.min(height, maxY + paddingY + 1);
            bounds = new TrackPageImageDTOs.Crop(
                round((double) left / width),
                round((double) top / height),
                round((double) (right - left) / width),
                round((double) (bottom - top) / height)
            );
            borderRatio = round(1d - bounds.width() * bounds.height());
        }

        double mean = sum / Math.max(1, samples);
        double variance = Math.max(0, sumSquares / Math.max(1, samples) - mean * mean);
        double brightness = round(mean / 255d);
        double contrast = round(Math.min(1, Math.sqrt(variance) / 127.5d));
        double skew = estimateSkew(image, step);
        List<String> suggestions = new ArrayList<>();
        if (borderRatio >= 0.04) suggestions.add("AUTO_CROP");
        if (Math.abs(skew) >= 0.3) suggestions.add("DESKEW");
        if (contrast < 0.45) suggestions.add("AUTO_CONTRAST");
        return new TrackPageImageDTOs.Analysis(mediaId, width, height, bounds, borderRatio, skew, brightness, contrast, suggestions, warnings);
    }

    public List<byte[]> transform(byte[] content, TrackPageImageDTOs.EditRequest request) {
        PageEditRecipe recipe = toRecipe(request);
        validateRecipe(recipe);
        if (isEmpty(recipe)) throw badRequest("Image recipe does not contain changes", "image.recipe.empty");
        return transformRendered(decode(content), recipe).stream().map(this::encode).toList();
    }

    /** Applies the same transformation pipeline to an image rendered from a PDF page. */
    public List<BufferedImage> transformRendered(BufferedImage source, TrackPageImageDTOs.EditRequest request) {
        return transformRendered(source, toRecipe(request));
    }

    @Override
    public List<BufferedImage> transformRendered(BufferedImage source, PageEditRecipe recipe) {
        validateRecipe(recipe);
        BufferedImage image = source;
        int turns = Math.floorMod(recipe.rotationQuarterTurns(), 4);
        for (int i = 0; i < turns; i++) image = rotateClockwise(image);
        if (!isZeroAngle(recipe.deskewDegrees())) image = rotateFine(image, recipe.deskewDegrees());

        List<PageEditRecipe.Crop> crops = recipe.crops().isEmpty()
            ? List.of(new PageEditRecipe.Crop(0, 0, 1, 1))
            : recipe.crops();
        List<BufferedImage> results = new ArrayList<>(crops.size());
        for (PageEditRecipe.Crop crop : crops) results.add(adjust(crop(image, crop), recipe));
        return results;
    }

    /** I DTO esposti via HTTP portano i vincoli di bean validation; la pipeline lavora sul modello neutro. */
    private static PageEditRecipe toRecipe(TrackPageImageDTOs.EditRequest request) {
        if (request == null) return null;
        List<PageEditRecipe.Crop> crops = request.crops() == null
            ? null
            : request.crops().stream().map(crop -> new PageEditRecipe.Crop(crop.x(), crop.y(), crop.width(), crop.height())).toList();
        return new PageEditRecipe(
            request.recipeVersion(),
            request.rotationQuarterTurns(),
            request.deskewDegrees(),
            crops,
            request.grayscale(),
            request.brightness(),
            request.contrast(),
            request.autoContrast(),
            request.threshold()
        );
    }

    private byte[] encode(BufferedImage image) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, "png", output)) throw new IOException("PNG writer is not available");
            byte[] bytes = output.toByteArray();
            if (bytes.length > MAX_BYTES) throw new RequestAlertException(HttpStatus.PAYLOAD_TOO_LARGE, "Resulting image is too large", "trackPageImage", "image.output.tooLarge");
            return bytes;
        } catch (IOException exception) {
            throw new RequestAlertException(HttpStatus.UNPROCESSABLE_ENTITY, "Unable to encode image", "trackPageImage", "image.encode");
        }
    }

    private BufferedImage decode(byte[] content) {
        if (content == null || content.length == 0) throw badRequest("Image is empty", "image.empty");
        if (content.length > MAX_BYTES) throw new RequestAlertException(HttpStatus.PAYLOAD_TOO_LARGE, "Image is too large", "trackPageImage", "image.tooLarge");
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(content));
            if (image == null) throw new RequestAlertException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported image format", "trackPageImage", "image.unsupported");
            if (image.getWidth() > MAX_SIDE || image.getHeight() > MAX_SIDE || (long) image.getWidth() * image.getHeight() > MAX_PIXELS) {
                throw new RequestAlertException(HttpStatus.PAYLOAD_TOO_LARGE, "Decoded image is too large", "trackPageImage", "image.dimensions.tooLarge");
            }
            return image;
        } catch (IOException exception) {
            throw new RequestAlertException(HttpStatus.UNPROCESSABLE_ENTITY, "Unable to decode image", "trackPageImage", "image.decode");
        }
    }

    private BufferedImage adjust(BufferedImage source, PageEditRecipe recipe) {
        boolean monochrome = recipe.grayscale() || recipe.threshold() != null;
        BufferedImage image = new BufferedImage(
            source.getWidth(),
            source.getHeight(),
            monochrome ? BufferedImage.TYPE_BYTE_GRAY : BufferedImage.TYPE_INT_RGB
        );
        int min = 255;
        int max = 0;
        if (recipe.autoContrast()) {
            for (int y = 0; y < source.getHeight(); y++) for (int x = 0; x < source.getWidth(); x++) {
                int l = luminance(source.getRGB(x, y));
                min = Math.min(min, l);
                max = Math.max(max, l);
            }
        }
        double contrastFactor = 1d + recipe.contrast() / 100d;
        int brightnessOffset = (int) Math.round(recipe.brightness() * 2.55d);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int sourceRgb = source.getRGB(x, y);
                int rgb;
                if (monochrome) {
                    int value = adjustValue(luminance(sourceRgb), min, max, recipe.autoContrast(), contrastFactor, brightnessOffset);
                    if (recipe.threshold() != null) value = value >= recipe.threshold() ? 255 : 0;
                    rgb = new Color(value, value, value).getRGB();
                } else {
                    int red = adjustValue((sourceRgb >> 16) & 0xff, min, max, recipe.autoContrast(), contrastFactor, brightnessOffset);
                    int green = adjustValue((sourceRgb >> 8) & 0xff, min, max, recipe.autoContrast(), contrastFactor, brightnessOffset);
                    int blue = adjustValue(sourceRgb & 0xff, min, max, recipe.autoContrast(), contrastFactor, brightnessOffset);
                    rgb = new Color(red, green, blue).getRGB();
                }
                image.setRGB(x, y, rgb);
            }
        }
        return image;
    }

    private static int adjustValue(int value, int min, int max, boolean autoContrast, double contrastFactor, int brightnessOffset) {
        if (autoContrast && max > min) value = (value - min) * 255 / (max - min);
        return clamp((int) Math.round((value - 128) * contrastFactor + 128) + brightnessOffset, 0, 255);
    }

    private static BufferedImage rotateClockwise(BufferedImage source) {
        BufferedImage target = new BufferedImage(source.getHeight(), source.getWidth(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, target.getWidth(), target.getHeight());
        graphics.translate(target.getWidth(), 0);
        graphics.rotate(Math.PI / 2);
        graphics.drawImage(source, 0, 0, null);
        graphics.dispose();
        return target;
    }

    private static BufferedImage rotateFine(BufferedImage source, double degrees) {
        double normalizedDegrees = normalizeAngle(degrees);
        if (normalizedDegrees < 0.001) return source;
        double radians = Math.toRadians(normalizedDegrees);
        BufferedImage target = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, target.getWidth(), target.getHeight());
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        graphics.translate(target.getWidth() / 2d, target.getHeight() / 2d);
        graphics.rotate(radians);
        graphics.drawImage(source, AffineTransform.getTranslateInstance(-source.getWidth() / 2d, -source.getHeight() / 2d), null);
        graphics.dispose();
        return target;
    }

    private static BufferedImage crop(BufferedImage source, PageEditRecipe.Crop crop) {
        int x = clamp((int) Math.round(crop.x() * source.getWidth()), 0, source.getWidth() - 1);
        int y = clamp((int) Math.round(crop.y() * source.getHeight()), 0, source.getHeight() - 1);
        int width = clamp((int) Math.round(crop.width() * source.getWidth()), 1, source.getWidth() - x);
        int height = clamp((int) Math.round(crop.height() * source.getHeight()), 1, source.getHeight() - y);
        BufferedImage target = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = target.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, width, height);
        graphics.drawImage(source, 0, 0, width, height, x, y, x + width, y + height, null);
        graphics.dispose();
        return target;
    }

    private static void validateCrops(List<PageEditRecipe.Crop> crops) {
        if (crops == null || crops.size() > 8) throw badRequest("Invalid crop regions", "image.crops.invalid");
        for (PageEditRecipe.Crop crop : crops) {
            if (crop == null || crop.x() < 0 || crop.y() < 0 || crop.width() < 0.02 || crop.height() < 0.02 || crop.x() + crop.width() > 1.000001 || crop.y() + crop.height() > 1.000001) {
                throw badRequest("Invalid crop region", "image.crop.invalid");
            }
        }
    }

    private static void validateRecipe(PageEditRecipe recipe) {
        if (recipe == null || recipe.recipeVersion() != 1) throw badRequest("Unsupported image recipe version", "image.recipeVersion");
        // Persisted PDF annotations may still use the legacy -5..0 range; HTTP image edits are constrained to 0..360 by bean validation.
        boolean invalidDeskew = !Double.isFinite(recipe.deskewDegrees()) || recipe.deskewDegrees() < -5 || recipe.deskewDegrees() > 360;
        if (recipe.rotationQuarterTurns() < -3 || recipe.rotationQuarterTurns() > 3 || invalidDeskew || recipe.brightness() < -100 || recipe.brightness() > 100 || recipe.contrast() < -100 || recipe.contrast() > 100 || (recipe.threshold() != null && (recipe.threshold() < 0 || recipe.threshold() > 255))) {
            throw badRequest("Invalid image adjustments", "image.recipe.invalid");
        }
        validateCrops(recipe.crops());
    }

    private static boolean isEmpty(PageEditRecipe recipe) {
        boolean fullCrop = recipe.crops().isEmpty() ||
            (recipe.crops().size() == 1 && recipe.crops().get(0).x() == 0 && recipe.crops().get(0).y() == 0 && recipe.crops().get(0).width() == 1 && recipe.crops().get(0).height() == 1);
        return recipe.rotationQuarterTurns() == 0 && isZeroAngle(recipe.deskewDegrees()) && fullCrop && !recipe.grayscale() &&
            recipe.brightness() == 0 && recipe.contrast() == 0 && !recipe.autoContrast() && recipe.threshold() == null;
    }

    private static boolean isZeroAngle(double degrees) {
        return normalizeAngle(degrees) < 0.001;
    }

    private static double normalizeAngle(double degrees) {
        return ((degrees % 360) + 360) % 360;
    }

    private static double estimateSkew(BufferedImage image, int baseStep) {
        int step = Math.max(baseStep, Math.max(image.getWidth(), image.getHeight()) / 1200);
        double bestAngle = 0;
        double bestScore = Double.NEGATIVE_INFINITY;
        int margin = Math.max(2, image.getWidth() / 10);
        for (int half = -10; half <= 10; half++) {
            double angle = half / 2d;
            double tangent = Math.tan(Math.toRadians(angle));
            int[] rows = new int[image.getHeight() + margin * 2];
            for (int y = 0; y < image.getHeight(); y += step) for (int x = 0; x < image.getWidth(); x += step) {
                if (luminance(image.getRGB(x, y)) >= 180) continue;
                int row = (int) Math.round(y + tangent * (x - image.getWidth() / 2d)) + margin;
                if (row >= 0 && row < rows.length) rows[row]++;
            }
            double score = 0;
            for (int count : rows) score += (double) count * count;
            if (score > bestScore) {
                bestScore = score;
                bestAngle = angle;
            }
        }
        return round(bestAngle);
    }

    private static int luminance(int rgb) {
        int red = (rgb >> 16) & 0xff;
        int green = (rgb >> 8) & 0xff;
        int blue = rgb & 0xff;
        return (red * 299 + green * 587 + blue * 114) / 1000;
    }

    private static int clamp(int value, int minimum, int maximum) { return Math.max(minimum, Math.min(maximum, value)); }
    private static double round(double value) { return Math.round(value * 1000d) / 1000d; }
    private static RequestAlertException badRequest(String message, String key) { return new RequestAlertException(HttpStatus.BAD_REQUEST, message, "trackPageImage", key); }
}
