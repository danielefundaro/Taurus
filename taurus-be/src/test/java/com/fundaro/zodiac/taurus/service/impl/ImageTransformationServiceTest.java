package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.service.dto.TrackPageImageDTOs;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageTransformationServiceTest {

    private final ImageTransformationService service = new ImageTransformationService();

    @Test
    void detectsBlankBordersAndSuggestsCrop() throws Exception {
        BufferedImage image = new BufferedImage(200, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, 200, 300);
        graphics.setColor(Color.BLACK);
        graphics.fillRect(30, 40, 140, 220);
        graphics.dispose();

        TrackPageImageDTOs.Analysis analysis = service.analyze(7L, png(image));

        assertThat(analysis.width()).isEqualTo(200);
        assertThat(analysis.height()).isEqualTo(300);
        assertThat(analysis.blankBorderRatio()).isGreaterThan(0.2);
        assertThat(analysis.suggestions()).contains("AUTO_CROP");
        assertThat(analysis.contentBounds().x()).isBetween(0.1, 0.2);
    }

    @Test
    void rotatesThenCropsAndProducesPng() throws Exception {
        BufferedImage source = new BufferedImage(100, 200, BufferedImage.TYPE_INT_RGB);
        TrackPageImageDTOs.EditRequest recipe = new TrackPageImageDTOs.EditRequest(
            1L,
            1,
            1,
            0,
            List.of(new TrackPageImageDTOs.Crop(0, 0, 0.5, 1)),
            true,
            0,
            0,
            false,
            null
        );

        BufferedImage result = ImageIO.read(new ByteArrayInputStream(service.transform(png(source), recipe).get(0)));

        assertThat(result.getWidth()).isEqualTo(100);
        assertThat(result.getHeight()).isEqualTo(100);
    }

    @Test
    void createsOneImageForEachCropRegion() throws Exception {
        BufferedImage source = new BufferedImage(200, 100, BufferedImage.TYPE_INT_RGB);
        TrackPageImageDTOs.EditRequest recipe = new TrackPageImageDTOs.EditRequest(
            1L,
            1,
            0,
            0,
            List.of(new TrackPageImageDTOs.Crop(0, 0, 0.5, 1), new TrackPageImageDTOs.Crop(0.5, 0, 0.5, 1)),
            false,
            0,
            0,
            false,
            null
        );

        var results = service.transform(png(source), recipe);

        assertThat(results).hasSize(2);
        assertThat(ImageIO.read(new ByteArrayInputStream(results.get(0)))).extracting(BufferedImage::getWidth, BufferedImage::getHeight).containsExactly(100, 100);
        assertThat(ImageIO.read(new ByteArrayInputStream(results.get(1)))).extracting(BufferedImage::getWidth, BufferedImage::getHeight).containsExactly(100, 100);
    }

    @Test
    void rejectsAnEmptyRecipe() throws Exception {
        TrackPageImageDTOs.EditRequest recipe = new TrackPageImageDTOs.EditRequest(1L, 1, 0, 0, List.of(), false, 0, 0, false, null);

        assertThatThrownBy(() -> service.transform(png(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB)), recipe))
            .isInstanceOf(RequestAlertException.class)
            .hasMessageContaining("does not contain changes");
    }

    @Test
    void appliesManualAdjustmentsToAlreadyRenderedPdfPages() {
        BufferedImage source = new BufferedImage(20, 10, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = source.createGraphics();
        graphics.setColor(Color.RED);
        graphics.fillRect(0, 0, 20, 10);
        graphics.dispose();
        TrackPageImageDTOs.EditRequest recipe = new TrackPageImageDTOs.EditRequest(0L, 1, 1, 0, List.of(), true, 10, 5, false, null);

        BufferedImage result = service.transformRendered(source, recipe).get(0);

        assertThat(result.getWidth()).isEqualTo(10);
        assertThat(result.getHeight()).isEqualTo(20);
        assertThat(result.getColorModel().getNumColorComponents()).isEqualTo(1);
    }

    @Test
    void keepsTheCanvasFixedAndClipsAnglesAcrossTheFullCircle() throws Exception {
        BufferedImage source = new BufferedImage(100, 200, BufferedImage.TYPE_INT_RGB);
        TrackPageImageDTOs.EditRequest recipe = new TrackPageImageDTOs.EditRequest(1L, 1, 0, 45, List.of(), false, 0, 0, false, null);

        BufferedImage result = ImageIO.read(new ByteArrayInputStream(service.transform(png(source), recipe).get(0)));

        assertThat(result.getWidth()).isEqualTo(100);
        assertThat(result.getHeight()).isEqualTo(200);
        assertThat(new Color(result.getRGB(0, 0))).isEqualTo(Color.WHITE);
        assertThat(new Color(result.getRGB(5, 100))).isEqualTo(Color.BLACK);
        assertThat(new Color(result.getRGB(50, 100))).isEqualTo(Color.BLACK);
    }

    @Test
    void treatsThreeHundredAndSixtyDegreesAsTheZeroAngle() throws Exception {
        TrackPageImageDTOs.EditRequest recipe = new TrackPageImageDTOs.EditRequest(1L, 1, 0, 360, List.of(), false, 0, 0, false, null);

        assertThatThrownBy(() -> service.transform(png(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB)), recipe))
            .isInstanceOf(RequestAlertException.class)
            .hasMessageContaining("does not contain changes");
    }

    @Test
    void refusesAnOversizedImageFromItsHeaderBeforeDecodingThePixels() {
        // A header announcing 30000 x 30000 pixels, without the pixels: decoding it would fail differently.
        assertThatThrownBy(() -> service.analyze(7L, pngHeader(30_000, 30_000)))
            .isInstanceOf(RequestAlertException.class)
            .hasMessageContaining("Decoded image is too large");
    }

    /**
     * Una pagina A4 a 300 DPI, la dimensione di lavoro reale: analisi e ricetta completa devono
     * restare nell'ordine dei secondi. Il margine è larghissimo rispetto ai tempi misurati perché
     * la prova deve cogliere una regressione di ordine di grandezza, non la lentezza di una
     * macchina occupata; il collaudo sulle scansioni vere resta un'altra cosa.
     */
    @Test
    void staysWithinTheTimeBudgetOnAnA4PageAt300Dpi() throws Exception {
        byte[] page = png(scan(2480, 3508));

        long analysisStarted = System.nanoTime();
        TrackPageImageDTOs.Analysis analysis = service.analyze(11L, page);
        long analysisMillis = (System.nanoTime() - analysisStarted) / 1_000_000L;

        long transformStarted = System.nanoTime();
        List<byte[]> results = service.transform(page, new TrackPageImageDTOs.EditRequest(
            1L,
            1,
            1,
            359.6,
            List.of(new TrackPageImageDTOs.Crop(0, 0, 0.5, 1), new TrackPageImageDTOs.Crop(0.5, 0, 0.5, 1)),
            true,
            5,
            10,
            true,
            null
        ));
        long transformMillis = (System.nanoTime() - transformStarted) / 1_000_000L;

        assertThat(analysis.width()).isEqualTo(2480);
        assertThat(results).hasSize(2);
        assertThat(analysisMillis).as("analisi di una pagina A4 a 300 DPI in ms").isLessThan(5_000);
        assertThat(transformMillis).as("rotazione, raddrizzamento, due ritagli e toni in ms").isLessThan(20_000);
    }

    /** Una pagina con righe di pentagramma: contenuto ovunque, come in una scansione vera. */
    private static BufferedImage scan(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, width, height);
        graphics.setColor(Color.BLACK);
        for (int line = 0; line < height - 200; line += 24) graphics.drawLine(120, 100 + line, width - 120, 100 + line);
        graphics.dispose();
        return image;
    }

    private static byte[] pngHeader(int width, int height) {
        ByteBuffer header = ByteBuffer.allocate(13).putInt(width).putInt(height).put((byte) 8).put((byte) 2).put((byte) 0).put((byte) 0).put((byte) 0);
        ByteBuffer png = ByteBuffer.allocate(8 + 25 + 12);
        png.put(new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
        chunk(png, "IHDR", header.array());
        chunk(png, "IEND", new byte[0]);
        return png.array();
    }

    private static void chunk(ByteBuffer png, String type, byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(type.getBytes(StandardCharsets.US_ASCII));
        crc.update(data);
        png.putInt(data.length).put(type.getBytes(StandardCharsets.US_ASCII)).put(data).putInt((int) crc.getValue());
    }

    private static byte[] png(BufferedImage image) throws Exception {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }
}
