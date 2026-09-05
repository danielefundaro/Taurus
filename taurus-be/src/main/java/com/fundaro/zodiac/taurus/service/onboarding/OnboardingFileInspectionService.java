package com.fundaro.zodiac.taurus.service.onboarding;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.onboarding.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.apache.commons.csv.*;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

@Service
public class OnboardingFileInspectionService {
    public record ParsedRow(OnboardingSection section, int rowNumber, LinkedHashMap<String, String> values) {}
    public record Inspection(List<ParsedRow> rows, List<String> warnings) {}
    public static class InspectionException extends RuntimeException {
        private final String code;
        public InspectionException(String code, String message) { super(message); this.code = code; }
        public InspectionException(String code, String message, Throwable cause) { super(message, cause); this.code = code; }
        public String getCode() { return code; }
    }

    private final ApplicationProperties.OnboardingProperties properties;
    public OnboardingFileInspectionService(ApplicationProperties applicationProperties) { this.properties = applicationProperties.getOnboarding(); }

    public Inspection inspect(byte[] bytes, OnboardingImportFormat format, OnboardingSection csvSection, Set<OnboardingSection> selected) {
        if (bytes == null || bytes.length == 0) throw new InspectionException("FILE_EMPTY", "Il file è vuoto");
        if (bytes.length > properties.getMaxFileSize().toBytes()) throw new InspectionException("FILE_TOO_LARGE", "Il file supera il limite configurato");
        return format == OnboardingImportFormat.CSV ? csv(bytes, csvSection) : xlsx(bytes, selected);
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
                for (String header : section.getHeaders()) values.put(header, record.isMapped(header) ? record.get(header).trim() : "");
                if (!values.values().stream().allMatch(String::isBlank)) rows.add(new ParsedRow(section, Math.toIntExact(record.getRecordNumber() + 1), values));
                enforceRowLimit(rows.size(), section, rows.stream().filter(r -> r.section() == OnboardingSection.USERS).count());
            }
            return new Inspection(rows, List.of());
        } catch (IOException | IllegalArgumentException exception) {
            throw new InspectionException("FILE_CSV_INVALID", "Il CSV non rispetta il formato RFC 4180 o le intestazioni previste");
        }
    }

    private Inspection xlsx(byte[] bytes, Set<OnboardingSection> selected) {
        if (bytes.length < 4 || bytes[0] != 'P' || bytes[1] != 'K') throw new InspectionException("FILE_UNSUPPORTED_FORMAT", "Il file non è un workbook XLSX valido");
        ZipSecureFile.setMinInflateRatio(0.01d); ZipSecureFile.setMaxEntrySize(20_000_000L); ZipSecureFile.setMaxTextSize(10_000_000L);
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            if (workbook.getPackage().getParts().stream().anyMatch(part -> part.getPartName().getName().toLowerCase(Locale.ROOT).contains("vbaproject")))
                throw new InspectionException("MACRO_NOT_ALLOWED", "Il workbook contiene macro");
            if (!workbook.getExternalLinksTable().isEmpty()) throw new InspectionException("FILE_EXTERNAL_LINKS", "Il workbook contiene collegamenti esterni");
            Sheet metadata = workbook.getSheet("_taurus");
            if (metadata == null || metadata.getRow(1) == null || metadata.getRow(1).getCell(1) == null || !"1".equals(new DataFormatter(Locale.ROOT).formatCellValue(metadata.getRow(1).getCell(1))))
                throw new InspectionException("TEMPLATE_VERSION_UNSUPPORTED", "Versione del template assente o non supportata");
            List<ParsedRow> rows = new ArrayList<>(); List<String> warnings = new ArrayList<>(); long userRows = 0;
            for (Sheet sheet : workbook) {
                if ("Istruzioni".equals(sheet.getSheetName()) || "_taurus".equals(sheet.getSheetName())) continue;
                OnboardingSection section = OnboardingSection.fromSheet(sheet.getSheetName());
                if (section == null) { warnings.add("Foglio sconosciuto ignorato: " + sheet.getSheetName()); continue; }
                if (selected != null && !selected.isEmpty() && !selected.contains(section)) continue;
                Row headerRow = sheet.getRow(0);
                if (headerRow == null) throw new InspectionException("SHEET_HEADER_MISSING", "Intestazione mancante nel foglio " + sheet.getSheetName());
                List<String> headers = new ArrayList<>();
                for (Cell cell : headerRow) headers.add(cell.getStringCellValue().trim());
                validateHeaders(section, headers);
                for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                    Row source = sheet.getRow(rowIndex); if (source == null) continue;
                    LinkedHashMap<String, String> values = new LinkedHashMap<>(); boolean empty = true;
                    for (int column = 0; column < section.getHeaders().size(); column++) {
                        Cell cell = source.getCell(column, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
                        if (cell != null && cell.getCellType() == CellType.FORMULA) throw new InspectionException("FORMULA_NOT_ALLOWED", "Le formule non sono ammesse (" + sheet.getSheetName() + ", riga " + (rowIndex + 1) + ")");
                        String value = cellValue(cell); values.put(section.getHeaders().get(column), value); empty &= value.isBlank();
                        if (value.length() > properties.getMaxCellLength()) throw new InspectionException("CELL_TOO_LONG", "Una cella supera il limite configurato");
                    }
                    if (!empty) { rows.add(new ParsedRow(section, rowIndex + 1, values)); if (section == OnboardingSection.USERS) userRows++; }
                    enforceRowLimit(rows.size(), section, userRows);
                }
            }
            if (selected != null) for (OnboardingSection section : selected) if (workbook.getSheet(section.getSheetName()) == null)
                throw new InspectionException("SHEET_REQUIRED", "Foglio richiesto mancante: " + section.getSheetName());
            return new Inspection(rows, warnings);
        } catch (InspectionException exception) { throw exception;
        } catch (Exception exception) { throw new InspectionException("FILE_XLSX_INVALID", "Il workbook è danneggiato, cifrato o non supportato", exception); }
    }

    private String cellValue(Cell cell) {
        if (cell == null) return "";
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell))
            return cell.getLocalDateTimeCellValue().toLocalDate().format(DateTimeFormatter.ISO_DATE);
        return new DataFormatter(Locale.ROOT, true).formatCellValue(cell).trim();
    }
    private void validateHeaders(OnboardingSection section, List<String> actual) {
        if (actual.size() > properties.getMaxColumns()) throw new InspectionException("TOO_MANY_COLUMNS", "Il file supera il limite di colonne");
        for (String header : actual) if (!section.getHeaders().contains(header)) throw new InspectionException("COLUMN_UNKNOWN", "Colonna sconosciuta: " + header);
        if (!actual.equals(section.getHeaders())) throw new InspectionException("COLUMN_MISMATCH", "Le intestazioni devono coincidere e mantenere l'ordine del template");
    }
    private void enforceRowLimit(long rows, OnboardingSection section, long userRows) {
        if (rows > properties.getMaxTotalRows()) throw new InspectionException("TOO_MANY_ROWS", "Il file supera il limite complessivo di righe");
        if (userRows > properties.getMaxUserRows()) throw new InspectionException("TOO_MANY_USER_ROWS", "Il file supera il limite di righe utenti");
    }
    private long count(String value, char needle) { return value.chars().filter(c -> c == needle).count(); }
}
