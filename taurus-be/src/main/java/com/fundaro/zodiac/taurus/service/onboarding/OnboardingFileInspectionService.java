package com.fundaro.zodiac.taurus.service.onboarding;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.onboarding.*;

import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.csv.*;
import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackagePart;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.model.StylesTable;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.springframework.stereotype.Service;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

@Service
public class OnboardingFileInspectionService {
    public record ParsedRow(OnboardingSection section, int rowNumber, LinkedHashMap<String, String> values) {
    }

    public record Inspection(List<ParsedRow> rows, List<String> warnings) {
    }

    public static class InspectionException extends RuntimeException {
        private final String code;

        public InspectionException(String code, String message) {
            super(message);
            this.code = code;
        }

        public InspectionException(String code, String message, Throwable cause) {
            super(message, cause);
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    private static final String METADATA_SHEET = "_taurus";
    private final ApplicationProperties.OnboardingProperties properties;

    public OnboardingFileInspectionService(ApplicationProperties applicationProperties) {
        this.properties = applicationProperties.getOnboarding();
    }

    public Inspection inspect(byte[] bytes, OnboardingImportFormat format, OnboardingSection csvSection, Set<OnboardingSection> selected) {
        if (bytes == null || bytes.length == 0) throw new InspectionException("FILE_EMPTY", "Il file è vuoto");
        if (bytes.length > properties.getMaxFileSize().toBytes())
            throw new InspectionException("FILE_TOO_LARGE", "Il file supera il limite configurato");
        requireRealFormat(bytes, format);
        return format == OnboardingImportFormat.CSV ? csv(bytes, csvSection) : xlsx(bytes, selected);
    }

    /**
     * The format comes from the content, never from the extension or from the MIME type the client declared: a
     * workbook renamed {@code .csv}, or an old {@code .xls} renamed {@code .xlsx}, is refused here instead of
     * reaching a parser that would try to make sense of it.
     */
    private void requireRealFormat(byte[] bytes, OnboardingImportFormat format) {
        boolean zip = startsWith(bytes, 0x50, 0x4B);
        boolean ole2 = startsWith(bytes, 0xD0, 0xCF, 0x11, 0xE0);
        if (format == OnboardingImportFormat.XLSX && !zip)
            throw new InspectionException("FILE_UNSUPPORTED_FORMAT", ole2
                ? "Il file è una cartella Excel in formato .xls: salvarla come .xlsx"
                : "Il file non è un workbook XLSX valido");
        if (format != OnboardingImportFormat.CSV) return;
        if (zip || ole2)
            throw new InspectionException("FILE_UNSUPPORTED_FORMAT", "Il file non è un CSV di testo");
        for (byte value : bytes)
            if (value == 0)
                throw new InspectionException("FILE_UNSUPPORTED_FORMAT", "Il file non è un CSV di testo");
    }

    private static boolean startsWith(byte[] bytes, int... signature) {
        if (bytes.length < signature.length) return false;
        for (int index = 0; index < signature.length; index++)
            if ((bytes[index] & 0xFF) != signature[index]) return false;
        return true;
    }

    private Inspection csv(byte[] bytes, OnboardingSection section) {
        if (section == null) throw new InspectionException("CSV_SECTION_REQUIRED", "La sezione CSV è obbligatoria");
        String text;
        try {
            CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
            text = decoder.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException exception) {
            throw new InspectionException("FILE_ENCODING_INVALID", "Il CSV non è codificato integralmente in UTF-8");
        }
        if (text.startsWith("\uFEFF")) text = text.substring(1);
        String first = text.lines().findFirst().orElse("");
        char delimiter = count(first, ';') > count(first, ',') ? ';' : ',';
        try (CSVParser parser = CSVFormat.DEFAULT.builder().setDelimiter(delimiter).setHeader().setSkipHeaderRecord(true)
            .setIgnoreEmptyLines(true).setTrim(true).build().parse(new StringReader(text))) {
            validateHeaders(section, parser.getHeaderNames());
            List<ParsedRow> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                LinkedHashMap<String, String> values = new LinkedHashMap<>();
                for (String header : section.getHeaders())
                    values.put(header, record.isMapped(header) ? record.get(header).trim() : "");
                if (!values.values().stream().allMatch(String::isBlank))
                    rows.add(new ParsedRow(section, Math.toIntExact(record.getRecordNumber() + 1), values));
                enforceRowLimit(rows.size(), section, rows.stream().filter(r -> r.section() == OnboardingSection.USERS).count());
            }
            return new Inspection(rows, List.of());
        } catch (IOException | IllegalArgumentException exception) {
            throw new InspectionException("FILE_CSV_INVALID", "Il CSV non rispetta il formato RFC 4180 o le intestazioni previste");
        }
    }

    /**
     * Reads the workbook one sheet at a time with SAX: the cells are consumed as the parser meets them, so a file at
     * the size limit never becomes a whole object model in memory. The package is still opened, because the parts
     * tell what the workbook carries besides its sheets.
     */
    private Inspection xlsx(byte[] bytes, Set<OnboardingSection> selected) {
        ZipSecureFile.setMinInflateRatio(0.01d);
        ZipSecureFile.setMaxEntrySize(20_000_000L);
        ZipSecureFile.setMaxTextSize(10_000_000L);
        try (OPCPackage pkg = OPCPackage.open(new ByteArrayInputStream(bytes))) {
            rejectHostileParts(pkg);
            XSSFReader reader = new XSSFReader(pkg);
            ReadOnlySharedStringsTable strings = new ReadOnlySharedStringsTable(pkg);
            StylesTable styles = reader.getStylesTable();
            Set<String> sheetNames = new LinkedHashSet<>();
            String templateVersion = null;
            XSSFReader.SheetIterator metadata = (XSSFReader.SheetIterator) reader.getSheetsData();
            while (metadata.hasNext()) {
                try (InputStream sheet = metadata.next()) {
                    sheetNames.add(metadata.getSheetName());
                    if (!METADATA_SHEET.equals(metadata.getSheetName())) continue;
                    SingleCell version = new SingleCell(1, 1);
                    parse(sheet, styles, strings, METADATA_SHEET, version);
                    templateVersion = version.value();
                }
            }
            if (!String.valueOf(OnboardingTemplateService.VERSION).equals(templateVersion))
                throw new InspectionException("TEMPLATE_VERSION_UNSUPPORTED", "Versione del template assente o non supportata");
            if (selected != null) for (OnboardingSection section : selected)
                if (!sheetNames.contains(section.getSheetName()))
                    throw new InspectionException("SHEET_REQUIRED", "Foglio richiesto mancante: " + section.getSheetName());
            List<ParsedRow> rows = new ArrayList<>();
            List<String> warnings = new ArrayList<>();
            Tally tally = new Tally();
            XSSFReader.SheetIterator sheets = (XSSFReader.SheetIterator) reader.getSheetsData();
            while (sheets.hasNext()) {
                try (InputStream sheet = sheets.next()) {
                    String name = sheets.getSheetName();
                    if (METADATA_SHEET.equals(name) || "Istruzioni".equals(name)) continue;
                    OnboardingSection section = OnboardingSection.fromSheet(name);
                    if (section == null) {
                        warnings.add("Foglio sconosciuto ignorato: " + name);
                        continue;
                    }
                    if (selected != null && !selected.isEmpty() && !selected.contains(section)) continue;
                    parse(sheet, styles, strings, name, new SectionSink(section, name, rows, tally));
                }
            }
            return new Inspection(rows, warnings);
        } catch (InspectionException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new InspectionException("FILE_XLSX_INVALID", "Il workbook è danneggiato, cifrato o non supportato", exception);
        }
    }

    /**
     * What a workbook may carry besides sheets and styles. An external link part holds the remote references and the
     * DDE links of the workbook, an embedded object can carry one too: neither is ever needed by a template, and
     * refusing the part is the only way not to have to decide later whether to resolve it.
     */
    private void rejectHostileParts(OPCPackage pkg) throws InvalidFormatException {
        for (PackagePart part : pkg.getParts()) {
            String name = part.getPartName().getName().toLowerCase(Locale.ROOT);
            if (name.contains("vbaproject"))
                throw new InspectionException("MACRO_NOT_ALLOWED", "Il workbook contiene macro");
            if (name.contains("externallink"))
                throw new InspectionException("FILE_EXTERNAL_LINKS", "Il workbook contiene collegamenti esterni o DDE");
            if (name.contains("oleobject") || name.contains("activex") || name.contains("embeddings"))
                throw new InspectionException("FILE_EMBEDDED_OBJECT", "Il workbook contiene oggetti incorporati");
        }
    }

    private void parse(InputStream sheet, StylesTable styles, ReadOnlySharedStringsTable strings, String sheetName, CellSink sink)
        throws IOException, SAXException, ParserConfigurationException {
        XMLReader parser = XMLHelper.newXMLReader();
        parser.setContentHandler(new SheetHandler(styles, strings, sheetName, sink, properties.getMaxCellLength(), properties.getMaxCells()));
        parser.parse(new InputSource(sheet));
    }

    private void validateHeaders(OnboardingSection section, List<String> actual) {
        if (actual.size() > properties.getMaxColumns())
            throw new InspectionException("TOO_MANY_COLUMNS", "Il file supera il limite di colonne");
        for (String header : actual)
            if (!section.getHeaders().contains(header))
                throw new InspectionException("COLUMN_UNKNOWN", "Colonna sconosciuta: " + header);
        if (!section.acceptsHeaders(actual))
            throw new InspectionException("COLUMN_MISMATCH", "Le intestazioni devono coincidere e mantenere l'ordine del template");
    }

    /** Receives the cells of a sheet, in document order, with the blank ones left out. */
    private interface CellSink {
        void cell(int rowIndex, int columnIndex, String value);

        default void endRow(int rowIndex) {
        }

        default void endSheet() {
        }
    }

    /** One cell of the hidden metadata sheet: the template version. */
    private static final class SingleCell implements CellSink {
        private final int row;
        private final int column;
        private String value;

        private SingleCell(int row, int column) {
            this.row = row;
            this.column = column;
        }

        @Override
        public void cell(int rowIndex, int columnIndex, String value) {
            if (rowIndex == row && columnIndex == column) this.value = value;
        }

        private String value() {
            return value;
        }
    }

    /** Rows and user rows read so far, across every sheet of the workbook. */
    private static final class Tally {
        private int rows;
        private long userRows;
    }

    /** The data rows of one section: the first row is the header, the rest become parsed rows. */
    private final class SectionSink implements CellSink {
        private final OnboardingSection section;
        private final String sheetName;
        private final List<ParsedRow> rows;
        private final Tally tally;
        private final NavigableMap<Integer, String> current = new TreeMap<>();
        private boolean headerSeen;

        private SectionSink(OnboardingSection section, String sheetName, List<ParsedRow> rows, Tally tally) {
            this.section = section;
            this.sheetName = sheetName;
            this.rows = rows;
            this.tally = tally;
        }

        @Override
        public void cell(int rowIndex, int columnIndex, String value) {
            current.put(columnIndex, value);
        }

        @Override
        public void endRow(int rowIndex) {
            if (rowIndex == 0) {
                if (current.isEmpty())
                    throw new InspectionException("SHEET_HEADER_MISSING", "Intestazione mancante nel foglio " + sheetName);
                List<String> headers = new ArrayList<>();
                for (int column = 0; column <= current.lastKey(); column++)
                    headers.add(current.getOrDefault(column, ""));
                validateHeaders(section, headers);
                headerSeen = true;
                current.clear();
                return;
            }
            if (!current.isEmpty()) {
                LinkedHashMap<String, String> values = new LinkedHashMap<>();
                for (int column = 0; column < section.getHeaders().size(); column++)
                    values.put(section.getHeaders().get(column), current.getOrDefault(column, ""));
                rows.add(new ParsedRow(section, rowIndex + 1, values));
                tally.rows++;
                if (section == OnboardingSection.USERS) tally.userRows++;
                enforceRowLimit(tally.rows, section, tally.userRows);
            }
            current.clear();
        }

        @Override
        public void endSheet() {
            if (!headerSeen)
                throw new InspectionException("SHEET_HEADER_MISSING", "Intestazione mancante nel foglio " + sheetName);
        }
    }

    /**
     * The cells of one sheet, read as the parser meets them. A cell born from a formula is refused as soon as its
     * {@code f} element appears, even when the workbook stores the value it computed.
     */
    private static final class SheetHandler extends DefaultHandler {
        private final StylesTable styles;
        private final ReadOnlySharedStringsTable strings;
        private final DataFormatter formatter = new DataFormatter(Locale.ROOT, true);
        private final String sheetName;
        private final CellSink sink;
        private final int maxCellLength;
        private final int maxCells;
        private final StringBuilder characters = new StringBuilder();
        private final StringBuilder inline = new StringBuilder();
        private int cells;
        private int rowIndex = -1;
        private int columnIndex = -1;
        private String cellType;
        private short formatIndex;
        private String formatString;
        private String raw;
        private boolean capturing;

        private SheetHandler(StylesTable styles, ReadOnlySharedStringsTable strings, String sheetName, CellSink sink,
                             int maxCellLength, int maxCells) {
            this.styles = styles;
            this.strings = strings;
            this.sheetName = sheetName;
            this.sink = sink;
            this.maxCellLength = maxCellLength;
            this.maxCells = maxCells;
        }

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) {
            switch (name(localName, qName)) {
                case "row" -> rowIndex = number(attributes.getValue("r"), rowIndex + 2) - 1;
                case "c" -> startCell(attributes);
                case "f" -> throw new InspectionException("FORMULA_NOT_ALLOWED",
                    "Le formule non sono ammesse (" + sheetName + ", riga " + (rowIndex + 1) + ")");
                case "v", "t" -> {
                    capturing = true;
                    characters.setLength(0);
                }
                default -> {
                }
            }
        }

        @Override
        public void characters(char[] buffer, int start, int length) {
            if (capturing) characters.append(buffer, start, length);
        }

        @Override
        public void endElement(String uri, String localName, String qName) {
            switch (name(localName, qName)) {
                case "v" -> {
                    capturing = false;
                    raw = characters.toString();
                }
                case "t" -> {
                    capturing = false;
                    inline.append(characters);
                }
                case "c" -> endCell();
                case "row" -> sink.endRow(rowIndex);
                case "worksheet" -> sink.endSheet();
                default -> {
                }
            }
        }

        private void startCell(Attributes attributes) {
            String reference = attributes.getValue("r");
            columnIndex = reference == null ? columnIndex + 1 : new CellReference(reference).getCol();
            cellType = attributes.getValue("t");
            String style = attributes.getValue("s");
            XSSFCellStyle cellStyle = style == null ? null : styles.getStyleAt(number(style, 0));
            formatIndex = cellStyle == null ? 0 : cellStyle.getDataFormat();
            formatString = cellStyle == null ? null : cellStyle.getDataFormatString();
            raw = null;
            inline.setLength(0);
        }

        private void endCell() {
            if (++cells > maxCells)
                throw new InspectionException("TOO_MANY_CELLS", "Il file supera il limite di celle");
            String value = value().trim();
            if (value.length() > maxCellLength)
                throw new InspectionException("CELL_TOO_LONG", "Una cella supera il limite configurato");
            if (!value.isEmpty()) sink.cell(rowIndex, columnIndex, value);
        }

        private String value() {
            if ("inlineStr".equals(cellType)) return inline.toString();
            if (raw == null || raw.isEmpty()) return "";
            return switch (cellType == null ? "n" : cellType) {
                case "s" -> shared(raw);
                case "b" -> "1".equals(raw) ? "TRUE" : "FALSE";
                case "str", "e" -> raw;
                default -> numeric(raw);
            };
        }

        private String shared(String index) {
            try {
                return strings.getItemAt(Integer.parseInt(index)).getString();
            } catch (RuntimeException exception) {
                return "";
            }
        }

        /** A date keeps the ISO form the templates ask for, whatever format the workbook gives the cell. */
        private String numeric(String value) {
            try {
                double number = Double.parseDouble(value);
                if (DateUtil.isADateFormat(formatIndex, formatString) && DateUtil.isValidExcelDate(number))
                    return DateUtil.getLocalDateTime(number).toLocalDate().format(DateTimeFormatter.ISO_DATE);
                return formatter.formatRawCellContents(number, formatIndex, formatString);
            } catch (NumberFormatException exception) {
                return value;
            }
        }

        private static String name(String localName, String qName) {
            return localName == null || localName.isEmpty() ? qName : localName;
        }

        private static int number(String value, int fallback) {
            try {
                return value == null ? fallback : Integer.parseInt(value);
            } catch (NumberFormatException exception) {
                return fallback;
            }
        }
    }

    private void enforceRowLimit(long rows, OnboardingSection section, long userRows) {
        if (rows > properties.getMaxTotalRows())
            throw new InspectionException("TOO_MANY_ROWS", "Il file supera il limite complessivo di righe");
        if (userRows > properties.getMaxUserRows())
            throw new InspectionException("TOO_MANY_USER_ROWS", "Il file supera il limite di righe utenti");
    }

    private long count(String value, char needle) {
        return value.chars().filter(c -> c == needle).count();
    }
}
