package com.fundaro.zodiac.taurus.utils;

import com.fundaro.zodiac.taurus.utils.pdf.PdfAnnotations;
import com.fundaro.zodiac.taurus.utils.pdf.PdfCropRegion;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.logging.log4j.util.Strings;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.tools.imageio.ImageIOUtil;
import org.springframework.lang.NonNull;

public final class Converter {

    private Converter() {}

    public static String camelCaseToKebabCase(@NonNull String value) {
        return value.replaceAll("([a-z])([A-Z])", "$1-$2").toLowerCase();
    }

    public static String camelCaseToSnakeCase(@NonNull String value) {
        return value.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
    }

    public static String tenantConcatSnakeCase(@NonNull String tenantCode, @NonNull String value) {
        String normalizedTenant = tenantCode.replaceAll(" ", "").toLowerCase();
        return Arrays.stream(new String[]{normalizedTenant, value})
            .filter(Strings::isNotBlank)
            .collect(Collectors.joining("_"));
    }

    public static List<String> pdfToImage(byte[] content, String filename, String destinationPath) throws IOException {
        return pdfToImage(content, filename, destinationPath, null);
    }

    public static List<String> pdfToImage(
        byte[] content,
        String filename,
        String destinationPath,
        PdfAnnotations annotations
    ) throws IOException {
        int dpi = 300;
        List<String> files = new ArrayList<>();
        String normalizedFilename = filename.replace(" ", "_");
        Set<Integer> excludedPages = annotations != null && annotations.getExcludedPages() != null
            ? new HashSet<>(annotations.getExcludedPages())
            : Collections.emptySet();
        Map<Integer, List<PdfCropRegion>> cropMap = new HashMap<>();
        if (annotations != null && annotations.getCropRegions() != null) {
            annotations.getCropRegions().forEach(crop ->
                cropMap.computeIfAbsent(crop.getPage(), ignored -> new ArrayList<>()).add(crop)
            );
        }

        File destination = new File(Paths.get(destinationPath, normalizedFilename).toString());
        Files.createDirectories(destination.toPath());
        try (PDDocument document = Loader.loadPDF(content)) {
            PDFRenderer renderer = new PDFRenderer(document);
            for (int page = 0; page < document.getNumberOfPages(); page++) {
                int pageNumber = page + 1;
                if (excludedPages.contains(pageNumber)) {
                    files.add(null);
                    continue;
                }
                BufferedImage image;
                try {
                    image = renderer.renderImageWithDPI(page, dpi, ImageType.GRAY);
                } catch (RuntimeException exception) {
                    throw new IOException("PDF rendering failed for page " + pageNumber, exception);
                }
                List<PdfCropRegion> crops = cropMap.get(pageNumber);
                if (crops != null && !crops.isEmpty()) image = applyCrops(image, crops);
                String target = Paths.get(destination.getPath(), pageNumber + ".png").toString();
                ImageIOUtil.writeImage(image, target, dpi);
                files.add(target);
            }
        }
        return files;
    }

    private static BufferedImage applyCrops(BufferedImage image, List<PdfCropRegion> crops) {
        if (crops.size() == 1) return applyCrop(image, crops.get(0));
        List<BufferedImage> regions = crops.stream().map(crop -> applyCrop(image, crop)).toList();
        int totalHeight = regions.stream().mapToInt(BufferedImage::getHeight).sum();
        int maxWidth = regions.stream().mapToInt(BufferedImage::getWidth).max().orElse(1);
        BufferedImage combined = new BufferedImage(maxWidth, totalHeight, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D graphics = combined.createGraphics();
        int y = 0;
        for (BufferedImage region : regions) {
            graphics.drawImage(region, 0, y, null);
            y += region.getHeight();
        }
        graphics.dispose();
        return combined;
    }

    private static BufferedImage applyCrop(BufferedImage image, PdfCropRegion crop) {
        int x = clamp((int) (crop.getX() * image.getWidth()), 0, image.getWidth() - 1);
        int y = clamp((int) (crop.getY() * image.getHeight()), 0, image.getHeight() - 1);
        int width = clamp((int) (crop.getWidth() * image.getWidth()), 1, image.getWidth() - x);
        int height = clamp((int) (crop.getHeight() * image.getHeight()), 1, image.getHeight() - y);
        return image.getSubimage(x, y, width, height);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    public static byte[] objectToBytes(Object value) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ObjectOutputStream objects = new ObjectOutputStream(output)) {
            objects.writeObject(value);
            return output.toByteArray();
        }
    }

    public static Object bytesToObject(byte[] bytes) throws IOException, ClassNotFoundException {
        try (InputStream input = new ByteArrayInputStream(bytes); ObjectInputStream objects = new ObjectInputStream(input)) {
            return objects.readObject();
        }
    }

    public static String imageEncodeBase64(String filePath) throws IOException {
        return Base64.getEncoder().encodeToString(Files.readAllBytes(Paths.get(filePath)));
    }
}
