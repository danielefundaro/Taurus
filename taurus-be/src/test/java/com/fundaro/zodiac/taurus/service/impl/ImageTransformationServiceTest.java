package com.fundaro.zodiac.taurus.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fundaro.zodiac.taurus.service.dto.TrackPageImageDTOs;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

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

    private static byte[] png(BufferedImage image) throws Exception {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        }
    }
}
