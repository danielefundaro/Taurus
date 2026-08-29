package com.fundaro.zodiac.taurus.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class TenantLogoLoaderTest {

    private final TenantLogoLoader loader = new TenantLogoLoader();

    @Test
    void shouldResizeLargeLogoBeforeEmbeddingIt() throws Exception {
        BufferedImage source = new BufferedImage(1600, 800, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream sourceBytes = new ByteArrayOutputStream();
        ImageIO.write(source, "png", sourceBytes);

        byte[] normalizedBytes = loader.normalizeAsPng(sourceBytes.toByteArray()).orElseThrow();
        BufferedImage normalized = ImageIO.read(new ByteArrayInputStream(normalizedBytes));

        assertThat(normalized.getWidth()).isEqualTo(1200);
        assertThat(normalized.getHeight()).isEqualTo(600);
    }
}
