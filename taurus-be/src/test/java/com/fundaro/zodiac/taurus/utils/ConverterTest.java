package com.fundaro.zodiac.taurus.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.fundaro.zodiac.taurus.service.impl.ImageTransformationService;
import com.fundaro.zodiac.taurus.utils.pdf.PdfAnnotations;
import com.fundaro.zodiac.taurus.utils.pdf.PdfPageTransform;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConverterTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void appliesPageTransformsAndKeepsExcludedPagePositions() throws Exception {
        PdfAnnotations annotations = new PdfAnnotations();
        annotations.setExcludedPages(List.of(2));
        PdfPageTransform transform = new PdfPageTransform();
        transform.setPage(1);
        transform.setRotationQuarterTurns(1);
        transform.setGrayscale(true);
        annotations.setPageTransforms(List.of(transform));

        List<String> files = Converter.pdfToImage(pdfWithTwoLandscapePages(), "score.pdf", temporaryDirectory.toString(), annotations, new ImageTransformationService());

        assertThat(files).hasSize(2);
        assertThat(files.get(1)).isNull();
        BufferedImage firstPage = ImageIO.read(Path.of(files.get(0)).toFile());
        assertThat(firstPage.getHeight()).isGreaterThan(firstPage.getWidth());
        assertThat(firstPage.getColorModel().getNumColorComponents()).isEqualTo(1);
    }

    private static byte[] pdfWithTwoLandscapePages() throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (int pageIndex = 0; pageIndex < 2; pageIndex++) {
                PDPage page = new PDPage(new PDRectangle(100, 50));
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.setNonStrokingColor(Color.RED);
                    content.addRect(0, 0, 100, 50);
                    content.fill();
                }
            }
            document.save(output);
            return output.toByteArray();
        }
    }
}
