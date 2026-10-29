package com.fundaro.zodiac.taurus.service.onboarding;

import com.fundaro.zodiac.taurus.domain.onboarding.OnboardingSection;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

@Service
public class OnboardingTemplateService {
    public static final int VERSION = 1;
    /**
     * The coded values keep their technical form in the cells, because that is what the import reads; the Italian
     * label that explains each one lives in the legend of the instructions.
     */
    private static final Map<String, String> ROLE_LABELS = new LinkedHashMap<>();
    private static final Map<String, String> CONDITION_LABELS = new LinkedHashMap<>();
    private static final Map<String, String> DIRECTION_LABELS = new LinkedHashMap<>();
    private static final Map<String, String> ACCOUNT_TYPE_LABELS = new LinkedHashMap<>();

    static {
        ROLE_LABELS.put("ADMIN", "amministratore dell'istanza");
        ROLE_LABELS.put("TREASURER", "tesoriere, gestisce cassa e conti");
        ROLE_LABELS.put("ARCHIVIST", "archivista, gestisce brani e parti");
        ROLE_LABELS.put("USER", "socio");
        ROLE_LABELS.put("USER_EXTERNAL", "collaboratore esterno");
        CONDITION_LABELS.put("NEW", "nuovo");
        CONDITION_LABELS.put("EXCELLENT", "ottimo");
        CONDITION_LABELS.put("GOOD", "buono");
        CONDITION_LABELS.put("FAIR", "discreto");
        CONDITION_LABELS.put("TO_REPAIR", "da riparare");
        CONDITION_LABELS.put("OUT_OF_SERVICE", "fuori servizio");
        DIRECTION_LABELS.put("INCOME", "entrata");
        DIRECTION_LABELS.put("EXPENSE", "uscita");
        DIRECTION_LABELS.put("BOTH", "entrata e uscita");
        ACCOUNT_TYPE_LABELS.put("CASH", "cassa contanti");
        ACCOUNT_TYPE_LABELS.put("BANK", "conto bancario, ammette IBAN e banca");
    }

    public byte[] xlsx() {
        return xlsx(Set.of(OnboardingSection.values()));
    }

    public byte[] xlsx(Set<OnboardingSection> availableSections) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.getProperties().getCoreProperties().setCreator("Taurus");
            workbook.getProperties().getCoreProperties().setCreated(java.util.Optional.of(new Date(0)));
            CellStyle header = workbook.createCellStyle();
            Font font = workbook.createFont();
            font.setBold(true);
            header.setFont(font);
            header.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            instructions(workbook, header, availableSections);

            Sheet metadata = workbook.createSheet("_taurus");
            metadata.createRow(0).createCell(0).setCellValue("product");
            metadata.getRow(0).createCell(1).setCellValue("taurus");
            metadata.createRow(1).createCell(0).setCellValue("templateVersion");
            metadata.getRow(1).createCell(1).setCellValue(VERSION);
            workbook.setSheetHidden(workbook.getSheetIndex(metadata), true);

            for (OnboardingSection section : OnboardingSection.values()) {
                if (!availableSections.contains(section)) continue;
                Sheet sheet = workbook.createSheet(section.getSheetName());
                Row row = sheet.createRow(0);
                for (int i = 0; i < section.getHeaders().size(); i++) {
                    Cell cell = row.createCell(i);
                    cell.setCellValue(section.getHeaders().get(i));
                    cell.setCellStyle(header);
                    sheet.setColumnWidth(i, Math.min(12000, Math.max(4000, section.getHeaders().get(i).length() * 450)));
                }
                sheet.createFreezePane(0, 1);
                sheet.setAutoFilter(new org.apache.poi.ss.util.CellRangeAddress(0, 0, 0, section.getHeaders().size() - 1));
                addValidations(sheet, section);
            }
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to generate onboarding workbook", exception);
        }
    }

    public byte[] csv(OnboardingSection section) {
        String header = String.join(",", section.getHeaders()) + "\r\n";
        return header.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * The instructions, the legends of the coded columns and one example row per section. The examples live here and
     * not in the data sheets on purpose: an example left in a data sheet would be a well formed row, and the import
     * would create it without a word.
     */
    private void instructions(XSSFWorkbook workbook, CellStyle header, Set<OnboardingSection> availableSections) {
        Sheet sheet = workbook.createSheet("Istruzioni");
        int row = 0;
        text(sheet, row++, "Taurus - configurazione iniziale dell'istanza").setCellStyle(header);
        row++;
        text(sheet, row++, "Compilare solo i fogli necessari. Non rinominare fogli o colonne, non usare formule e non aggiungere collegamenti esterni.");
        text(sheet, row++, "Date: AAAA-MM-GG. Ruoli e riferimenti multipli: separatore |. L'importazione è esclusivamente additiva: non modifica né elimina nulla.");
        text(sheet, row++, "La colonna «riferimento» è una chiave locale a questo file: serve a citare una riga da un altro foglio e non viene salvata.");
        text(sheet, row++,
            "Strumenti, colonne facoltative per l'ascolto delle parti: suono = programma General MIDI da 0 a 127 oppure batteria:<nota>; " +
                "trasposizione e trasposizione_chiave_basso = semitoni dalla nota scritta al suono reale, es. -2 per gli strumenti in Si bemolle. " +
                "Se sono tutte vuote Taurus propone i valori dal nome dello strumento."
        );
        row++;
        text(sheet, row++, "Valori ammessi").setCellStyle(header);
        for (Map.Entry<String, String> entry : ROLE_LABELS.entrySet())
            text(sheet, row++, "ruoli · " + entry.getKey() + " = " + entry.getValue());
        if (availableSections.contains(OnboardingSection.INVENTORY))
            for (Map.Entry<String, String> entry : CONDITION_LABELS.entrySet())
                text(sheet, row++, "condizione · " + entry.getKey() + " = " + entry.getValue());
        if (availableSections.contains(OnboardingSection.CATEGORIES))
            for (Map.Entry<String, String> entry : DIRECTION_LABELS.entrySet())
                text(sheet, row++, "direzione · " + entry.getKey() + " = " + entry.getValue());
        if (availableSections.contains(OnboardingSection.ACCOUNTS))
            for (Map.Entry<String, String> entry : ACCOUNT_TYPE_LABELS.entrySet())
                text(sheet, row++, "tipo · " + entry.getKey() + " = " + entry.getValue());
        text(sheet, row++, "attivo · SI = l'utente può accedere · NO = l'utente resta sospeso");
        row++;
        text(sheet, row++, "Righe di esempio (da ricopiare nei fogli, non sono già presenti)").setCellStyle(header);
        for (OnboardingSection section : OnboardingSection.values()) {
            if (!availableSections.contains(section)) continue;
            text(sheet, row++, section.getSheetName() + " · " + example(section));
        }
        sheet.setColumnWidth(0, 20000);
    }

    private static Cell text(Sheet sheet, int rowIndex, String value) {
        Cell cell = sheet.createRow(rowIndex).createCell(0);
        cell.setCellValue(value);
        return cell;
    }

    /** One example per section, written as «column = value» so it stays readable beside the headers. */
    private static String example(OnboardingSection section) {
        List<String> values = switch (section) {
            case INSTRUMENTS -> List.of("TRB1", "Trombone", "Trombone tenore in Si bemolle", "57", "-2", "0");
            case USERS -> List.of("U1", "Maria", "Rossi", "maria.rossi@example.org", "1990-04-17", "USER|ARCHIVIST", "TRB1", "SI");
            case INVENTORY -> List.of("INV-001", "Trombone Yamaha", "Acquistato nel 2024", "1", "850.00", "EUR", "GOOD", "Ammaccatura sulla campana");
            case CATEGORIES -> List.of("Quote associative", "Quote annuali dei soci", "INCOME", "10");
            case ACCOUNTS -> List.of("CC1", "Conto corrente", "Conto principale", "BANK", "EUR", "IT60X0542811101000000123456", "Banca Esempio", "10");
            case OPENING_BALANCES -> List.of("CC1", "2026-01-01", "1250.00");
        };
        List<String> headers = section.getHeaders();
        StringBuilder result = new StringBuilder();
        for (int index = 0; index < headers.size() && index < values.size(); index++) {
            if (index > 0) result.append(" · ");
            result.append(headers.get(index)).append(" = ").append(values.get(index));
        }
        return result.toString();
    }

    private void addValidations(Sheet sheet, OnboardingSection section) {
        switch (section) {
            case USERS -> list(sheet, 7, "SI", "NO");
            case INVENTORY -> list(sheet, 6, "NEW", "EXCELLENT", "GOOD", "FAIR", "TO_REPAIR", "OUT_OF_SERVICE");
            case CATEGORIES -> list(sheet, 2, "INCOME", "EXPENSE", "BOTH");
            case ACCOUNTS -> list(sheet, 3, "CASH", "BANK");
            default -> {
            }
        }
    }

    private void list(Sheet sheet, int column, String... values) {
        DataValidationHelper helper = sheet.getDataValidationHelper();
        DataValidation validation = helper.createValidation(helper.createExplicitListConstraint(values),
            new org.apache.poi.ss.util.CellRangeAddressList(1, 5000, column, column));
        validation.setShowErrorBox(true);
        validation.setSuppressDropDownArrow(true);
        sheet.addValidationData(validation);
    }
}
