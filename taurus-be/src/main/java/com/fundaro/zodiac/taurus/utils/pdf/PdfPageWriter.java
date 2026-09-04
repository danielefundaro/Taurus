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
    private static final float TABLE_FONT_SIZE = 7;
    private static final float TABLE_LEADING = 8.5f;
    private static final float TABLE_CELL_PADDING = 3;
    private static final float TABLE_MIN_COLUMN_WIDTH = 30;
    private static final float TABLE_MAX_COLUMN_WIDTH = 130;
    private static final float EMPTY_TABLE_ROW_HEIGHT = TABLE_LEADING + 2 * TABLE_CELL_PADDING;

    private final PDDocument document;
    private final PDFont regular;
    private final PDFont bold;
    private final PDRectangle pageSize;
    private PDPage page;
    private PDPageContentStream stream;
    private float y;

    public PdfPageWriter(PDDocument document, PDFont regular, PDFont bold) throws IOException {
        this(document, regular, bold, PDRectangle.A4);
    }

    public PdfPageWriter(PDDocument document, PDFont regular, PDFont bold, PDRectangle pageSize) throws IOException {
        this.document = document;
        this.regular = regular;
        this.bold = bold;
        this.pageSize = pageSize;
        newPage();
    }

    public static boolean requiresLandscape(List<String> headers, List<List<String>> rows, PDFont regular, PDFont bold) throws IOException {
        return naturalColumnWidths(headers, rows, regular, bold).stream().reduce(0f, Float::sum) > PDRectangle.A4.getWidth() - 2 * MARGIN;
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

    public void table(List<String> headers, List<List<String>> rows) throws IOException {
        if (headers.isEmpty()) return;
        List<Float> widths = fittedColumnWidths(headers, rows);
        float headerHeight = rowHeight(wrappedCells(headers, bold, widths));
        table(headers, rows, widths, headerHeight);
    }

    public void tableSection(String title, List<String> headers, List<List<String>> rows) throws IOException {
        if (headers.isEmpty()) return;
        List<Float> widths = fittedColumnWidths(headers, rows);
        float headerHeight = rowHeight(wrappedCells(headers, bold, widths));
        float titleHeight = wrap(normalize(title), bold, 13, pageSize.getWidth() - 2 * MARGIN).size() * 18;
        float firstRowHeight = rows.isEmpty()
            ? EMPTY_TABLE_ROW_HEIGHT
            : rowHeight(wrappedCells(normalizedRow(rows.get(0), headers.size()), regular, widths));
        float maximumFirstRowHeight = pageSize.getHeight() - MARGIN - BOTTOM - titleHeight - headerHeight;
        ensure(titleHeight + headerHeight + Math.min(firstRowHeight, maximumFirstRowHeight));
        heading(title);
        table(headers, rows, widths, headerHeight);
    }

    private void table(List<String> headers, List<List<String>> rows, List<Float> widths, float headerHeight) throws IOException {
        drawTableHeader(headers, widths, headerHeight);

        if (rows.isEmpty()) {
            drawEmptyTableRow(widths);
            y -= 4;
            return;
        }

        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            List<List<String>> cells = wrappedCells(normalizedRow(rows.get(rowIndex), headers.size()), regular, widths);
            int lineCount = cells.stream().mapToInt(List::size).max().orElse(1);
            float fullHeight = lineCount * TABLE_LEADING + 2 * TABLE_CELL_PADDING;
            float freshPageCapacity = pageSize.getHeight() - MARGIN - BOTTOM - headerHeight;
            if (fullHeight > y - BOTTOM && fullHeight <= freshPageCapacity) {
                newPage();
                drawTableHeader(headers, widths, headerHeight);
            }

            int firstLine = 0;
            while (firstLine < lineCount) {
                int availableLines = (int) Math.floor((y - BOTTOM - 2 * TABLE_CELL_PADDING) / TABLE_LEADING);
                if (availableLines < 1) {
                    newPage();
                    drawTableHeader(headers, widths, headerHeight);
                    continue;
                }
                int linesToDraw = Math.min(lineCount - firstLine, availableLines);
                List<List<String>> chunk = new ArrayList<>();
                for (List<String> cell : cells) {
                    int end = Math.min(cell.size(), firstLine + linesToDraw);
                    chunk.add(firstLine >= cell.size() ? List.of() : cell.subList(firstLine, end));
                }
                drawTableRow(chunk, widths, regular, linesToDraw * TABLE_LEADING + 2 * TABLE_CELL_PADDING, rowIndex % 2 == 1);
                firstLine += linesToDraw;
                if (firstLine < lineCount) {
                    newPage();
                    drawTableHeader(headers, widths, headerHeight);
                }
            }
        }
        y -= 4;
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
        page = new PDPage(pageSize);
        document.addPage(page);
        stream = new PDPageContentStream(document, page);
        y = page.getMediaBox().getHeight() - MARGIN;
    }

    private void drawTableHeader(List<String> headers, List<Float> widths, float height) throws IOException {
        if (y - height < BOTTOM) newPage();
        drawTableRow(wrappedCells(headers, bold, widths), widths, bold, height, false, true);
    }

    private void drawEmptyTableRow(List<Float> widths) throws IOException {
        float tableWidth = widths.stream().reduce(0f, Float::sum);
        float bottom = y - EMPTY_TABLE_ROW_HEIGHT;
        stream.setNonStrokingColor(new Color(244, 247, 250));
        stream.addRect(MARGIN, bottom, tableWidth, EMPTY_TABLE_ROW_HEIGHT);
        stream.fill();
        stream.setLineWidth(0.45f);
        stream.setStrokingColor(new Color(190, 198, 209));
        stream.addRect(MARGIN, bottom, tableWidth, EMPTY_TABLE_ROW_HEIGHT);
        stream.stroke();
        stream.beginText();
        stream.setFont(regular, TABLE_FONT_SIZE);
        stream.setNonStrokingColor(new Color(90, 98, 110));
        stream.newLineAtOffset(MARGIN + TABLE_CELL_PADDING, y - TABLE_CELL_PADDING - TABLE_FONT_SIZE);
        stream.showText("Nessun dato disponibile");
        stream.endText();
        y = bottom;
    }

    private void drawTableRow(List<List<String>> cells, List<Float> widths, PDFont font, float height, boolean shaded) throws IOException {
        drawTableRow(cells, widths, font, height, shaded, false);
    }

    private void drawTableRow(
        List<List<String>> cells,
        List<Float> widths,
        PDFont font,
        float height,
        boolean shaded,
        boolean header
    ) throws IOException {
        float tableWidth = widths.stream().reduce(0f, Float::sum);
        float bottom = y - height;
        stream.setNonStrokingColor(header ? new Color(31, 52, 88) : shaded ? new Color(244, 247, 250) : Color.WHITE);
        stream.addRect(MARGIN, bottom, tableWidth, height);
        stream.fill();

        stream.setLineWidth(0.45f);
        stream.setStrokingColor(header ? new Color(31, 52, 88) : new Color(190, 198, 209));
        stream.addRect(MARGIN, bottom, tableWidth, height);
        float columnX = MARGIN;
        for (int index = 0; index < widths.size() - 1; index++) {
            columnX += widths.get(index);
            stream.moveTo(columnX, bottom);
            stream.lineTo(columnX, y);
        }
        stream.stroke();

        stream.setNonStrokingColor(header ? Color.WHITE : new Color(35, 42, 52));
        columnX = MARGIN;
        for (int column = 0; column < widths.size(); column++) {
            List<String> lines = column < cells.size() ? cells.get(column) : List.of();
            float textY = y - TABLE_CELL_PADDING - TABLE_FONT_SIZE;
            for (String line : lines) {
                stream.beginText();
                stream.setFont(font, TABLE_FONT_SIZE);
                stream.newLineAtOffset(columnX + TABLE_CELL_PADDING, textY);
                stream.showText(line);
                stream.endText();
                textY -= TABLE_LEADING;
            }
            columnX += widths.get(column);
        }
        y = bottom;
    }

    private List<Float> fittedColumnWidths(List<String> headers, List<List<String>> rows) throws IOException {
        List<Float> natural = naturalColumnWidths(headers, rows, regular, bold);
        float available = pageSize.getWidth() - 2 * MARGIN;
        float naturalTotal = natural.stream().reduce(0f, Float::sum);
        if (naturalTotal <= available) {
            float extra = (available - naturalTotal) / natural.size();
            return natural.stream().map(width -> width + extra).toList();
        }

        List<Float> minimum = new ArrayList<>();
        for (int index = 0; index < headers.size(); index++) {
            float headerWordWidth = widestWord(headers.get(index), bold, TABLE_FONT_SIZE) + 2 * TABLE_CELL_PADDING;
            minimum.add(Math.max(TABLE_MIN_COLUMN_WIDTH, Math.min(headerWordWidth, natural.get(index))));
        }
        float minimumTotal = minimum.stream().reduce(0f, Float::sum);
        if (minimumTotal >= available) {
            float scale = available / minimumTotal;
            return minimum.stream().map(width -> width * scale).toList();
        }

        float flexibleTotal = naturalTotal - minimumTotal;
        float flexibleAvailable = available - minimumTotal;
        List<Float> fitted = new ArrayList<>();
        for (int index = 0; index < natural.size(); index++) {
            float share = flexibleTotal == 0 ? 0 : (natural.get(index) - minimum.get(index)) / flexibleTotal;
            fitted.add(minimum.get(index) + flexibleAvailable * share);
        }
        return fitted;
    }

    private List<List<String>> wrappedCells(List<String> values, PDFont font, List<Float> widths) throws IOException {
        List<List<String>> cells = new ArrayList<>();
        for (int index = 0; index < widths.size(); index++) {
            String value = index < values.size() ? values.get(index) : "";
            cells.add(wrap(normalize(value), font, TABLE_FONT_SIZE, widths.get(index) - 2 * TABLE_CELL_PADDING));
        }
        return cells;
    }

    private static List<Float> naturalColumnWidths(
        List<String> headers,
        List<List<String>> rows,
        PDFont regular,
        PDFont bold
    ) throws IOException {
        List<Float> widths = new ArrayList<>();
        for (int column = 0; column < headers.size(); column++) {
            float width = textWidth(normalize(headers.get(column)), bold, TABLE_FONT_SIZE);
            for (List<String> row : rows) {
                if (column < row.size()) width = Math.max(width, textWidth(normalize(row.get(column)), regular, TABLE_FONT_SIZE));
            }
            widths.add(Math.max(TABLE_MIN_COLUMN_WIDTH, Math.min(width + 2 * TABLE_CELL_PADDING, TABLE_MAX_COLUMN_WIDTH)));
        }
        return widths;
    }

    private static List<String> normalizedRow(List<String> row, int columnCount) {
        List<String> result = new ArrayList<>(columnCount);
        for (int index = 0; index < columnCount; index++) result.add(index < row.size() ? row.get(index) : "");
        return result;
    }

    private static float rowHeight(List<List<String>> cells) {
        return cells.stream().mapToInt(List::size).max().orElse(1) * TABLE_LEADING + 2 * TABLE_CELL_PADDING;
    }

    private static float widestWord(String value, PDFont font, float size) throws IOException {
        float width = 0;
        for (String word : normalize(value).split("\\s+")) width = Math.max(width, textWidth(word, font, size));
        return width;
    }

    private static float textWidth(String value, PDFont font, float size) throws IOException {
        return font.getStringWidth(value) / 1000 * size;
    }

    private static List<String> wrap(String text, PDFont font, float size, float maxWidth) throws IOException {
        List<String> result = new ArrayList<>();
        String remaining = text == null || text.isBlank() ? "-" : text.trim();
        while (!remaining.isEmpty()) {
            if (textWidth(remaining, font, size) <= maxWidth) {
                result.add(remaining);
                break;
            }
            int fittingCharacters = 1;
            while (
                fittingCharacters < remaining.length() &&
                textWidth(remaining.substring(0, fittingCharacters + 1), font, size) <= maxWidth
            ) fittingCharacters++;
            int breakAt = remaining.lastIndexOf(' ', fittingCharacters);
            if (breakAt <= 0) breakAt = fittingCharacters;
            String line = remaining.substring(0, breakAt).stripTrailing();
            result.add(line.isEmpty() ? remaining.substring(0, fittingCharacters) : line);
            remaining = remaining.substring(breakAt).stripLeading();
        }
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
