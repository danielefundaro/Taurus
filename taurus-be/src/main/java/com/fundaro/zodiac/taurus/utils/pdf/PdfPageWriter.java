package com.fundaro.zodiac.taurus.utils.pdf;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;

public final class PdfPageWriter {

    private static final float MARGIN = 48;
    private static final float BOTTOM = 44;
    private static final float HEADER_LOGO_TOP_MARGIN = 10;
    private static final float HEADER_LOGO_BOTTOM_SPACING = 20;

    private final PDDocument document;
    private final PDFont regular;
    private final PDFont bold;
    private PDPage page;
    private PDPageContentStream stream;
    private float y;

    public PdfPageWriter(PDDocument document, PDFont regular, PDFont bold) throws IOException {
        this.document = document;
        this.regular = regular;
        this.bold = bold;
        newPage();
    }

    public void title(String text) throws IOException {
        writeWrapped(text, bold, 18, 24, 0, 28, 38, 76);
    }

    public void heading(String text) throws IOException {
        ensure(38);
        writeWrapped(text, bold, 13, 18, 0, 32, 71, 110);
    }

    public void subheading(String text) throws IOException {
        ensure(28);
        writeWrapped(text, bold, 11, 16, 0, 50, 75, 105);
    }

    public void line(String text, boolean emphasize) throws IOException {
        writeWrapped(text, emphasize ? bold : regular, 9.5f, 14, 0, 35, 35, 35);
    }

    public void space(float value) throws IOException {
        ensure(value);
        y -= value;
    }

    public void separator() throws IOException {
        ensure(24);
        y -= 8;
        stream.setStrokingColor(new Color(205, 210, 216));
        stream.moveTo(MARGIN, y);
        stream.lineTo(page.getMediaBox().getWidth() - MARGIN, y);
        stream.stroke();
        y -= 14;
    }

    public void image(byte[] bytes, String caption) throws IOException {
        ensure(180);
        PDImageXObject image = PDImageXObject.createFromByteArray(document, bytes, caption);
        float maxWidth = page.getMediaBox().getWidth() - 2 * MARGIN;
        float maxHeight = 160;
        float scale = Math.min(maxWidth / image.getWidth(), maxHeight / image.getHeight());
        float width = image.getWidth() * scale;
        float height = image.getHeight() * scale;
        stream.drawImage(image, MARGIN, y - height, width, height);
        y -= height + 4;
        writeWrapped("Foto: " + caption, regular, 8, 11, 0, 85, 85, 85);
        y -= 6;
    }

    public boolean headerLogo(byte[] bytes) {
        try {
            PDImageXObject image = PDImageXObject.createFromByteArray(document, bytes, "Logo tenant");
            float maxWidth = 120;
            float maxHeight = 56;
            float scale = Math.min(1, Math.min(maxWidth / image.getWidth(), maxHeight / image.getHeight()));
            float width = image.getWidth() * scale;
            float height = image.getHeight() * scale;
            float x = (page.getMediaBox().getWidth() - width) / 2;
            float logoTop = page.getMediaBox().getHeight() - HEADER_LOGO_TOP_MARGIN;
            stream.drawImage(image, x, logoTop - height, width, height);
            y = logoTop - height - HEADER_LOGO_BOTTOM_SPACING;
            return true;
        } catch (IOException | IllegalArgumentException exception) {
            return false;
        }
    }

    public void closeCurrentPage() throws IOException {
        if (stream != null) {
            stream.close();
            stream = null;
        }
    }

    public static void addPageNumbers(PDDocument document, PDFont font) throws IOException {
        int total = document.getNumberOfPages();
        for (int i = 0; i < total; i++) {
            PDPage currentPage = document.getPage(i);
            try (
                PDPageContentStream footer = new PDPageContentStream(
                    document,
                    currentPage,
                    PDPageContentStream.AppendMode.APPEND,
                    true,
                    true
                )
            ) {
                String text = "Pagina " + (i + 1) + " di " + total;
                float width = font.getStringWidth(text) / 1000 * 8;
                footer.beginText();
                footer.setFont(font, 8);
                footer.setNonStrokingColor(new Color(90, 90, 90));
                footer.newLineAtOffset((currentPage.getMediaBox().getWidth() - width) / 2, 22);
                footer.showText(text);
                footer.endText();
            }
        }
    }

    private void writeWrapped(
        String source,
        PDFont font,
        float size,
        float leading,
        float indent,
        int red,
        int green,
        int blue
    ) throws IOException {
        List<String> lines = wrap(normalize(source), font, size, page.getMediaBox().getWidth() - 2 * MARGIN - indent);
        ensure(lines.size() * leading + 2);
        stream.setNonStrokingColor(new Color(red, green, blue));
        stream.setFont(font, size);
        for (String line : lines) {
            stream.beginText();
            stream.newLineAtOffset(MARGIN + indent, y);
            stream.showText(line);
            stream.endText();
            y -= leading;
        }
    }

    private void ensure(float required) throws IOException {
        if (y - required < BOTTOM) newPage();
    }

    private void newPage() throws IOException {
        closeCurrentPage();
        page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        stream = new PDPageContentStream(document, page);
        y = page.getMediaBox().getHeight() - MARGIN;
    }

    private static List<String> wrap(String text, PDFont font, float size, float maxWidth) throws IOException {
        List<String> result = new ArrayList<>();
        String[] words = text.split("\\s+");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            String candidate = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && font.getStringWidth(candidate) / 1000 * size > maxWidth) {
                result.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(candidate);
            }
        }
        result.add(line.isEmpty() ? "-" : line.toString());
        return result;
    }

    private static String normalize(String text) {
        return text
            .replace("€", "EUR")
            .replace('\u2013', '-')
            .replace('\u2014', '-')
            .replace('\u2011', '-')
            .replace('\u00a0', ' ')
            .replaceAll("[^\\x20-\\x7EÀ-ÿ]", "?");
    }
}
