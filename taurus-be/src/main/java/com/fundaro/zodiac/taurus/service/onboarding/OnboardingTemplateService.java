package com.fundaro.zodiac.taurus.service.onboarding;

import com.fundaro.zodiac.taurus.domain.onboarding.OnboardingSection;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

@Service
public class OnboardingTemplateService {
    public static final int VERSION = 1;

    public byte[] xlsx() {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            workbook.getProperties().getCoreProperties().setCreator("Taurus");
            workbook.getProperties().getCoreProperties().setCreated(java.util.Optional.of(new Date(0)));
            CellStyle header = workbook.createCellStyle();
            Font font = workbook.createFont(); font.setBold(true); header.setFont(font);
            header.setFillForegroundColor(IndexedColors.LIGHT_CORNFLOWER_BLUE.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Sheet instructions = workbook.createSheet("Istruzioni");
            instructions.createRow(0).createCell(0).setCellValue("Taurus - configurazione iniziale tenant");
            instructions.createRow(2).createCell(0).setCellValue("Compilare solo i fogli necessari. Non rinominare fogli o colonne e non usare formule.");
            instructions.createRow(3).createCell(0).setCellValue("Date: YYYY-MM-DD. Ruoli e riferimenti multipli: separatore |. Importazione esclusivamente additiva.");
            instructions.setColumnWidth(0, 12000);

            Sheet metadata = workbook.createSheet("_taurus");
            metadata.createRow(0).createCell(0).setCellValue("product"); metadata.getRow(0).createCell(1).setCellValue("taurus");
            metadata.createRow(1).createCell(0).setCellValue("templateVersion"); metadata.getRow(1).createCell(1).setCellValue(VERSION);
            workbook.setSheetHidden(workbook.getSheetIndex(metadata), true);

            for (OnboardingSection section : OnboardingSection.values()) {
                Sheet sheet = workbook.createSheet(section.getSheetName());
                Row row = sheet.createRow(0);
                for (int i = 0; i < section.getHeaders().size(); i++) {
                    Cell cell = row.createCell(i); cell.setCellValue(section.getHeaders().get(i)); cell.setCellStyle(header);
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

    private void addValidations(Sheet sheet, OnboardingSection section) {
        switch (section) {
            case USERS -> list(sheet, 7, "SI", "NO");
            case INVENTORY -> list(sheet, 6, "NEW", "EXCELLENT", "GOOD", "FAIR", "TO_REPAIR", "OUT_OF_SERVICE");
            case CATEGORIES -> list(sheet, 2, "INCOME", "EXPENSE", "BOTH");
            case ACCOUNTS -> list(sheet, 3, "CASH", "BANK");
            default -> { }
        }
    }

    private void list(Sheet sheet, int column, String... values) {
        DataValidationHelper helper = sheet.getDataValidationHelper();
        DataValidation validation = helper.createValidation(helper.createExplicitListConstraint(values),
            new org.apache.poi.ss.util.CellRangeAddressList(1, 5000, column, column));
        validation.setShowErrorBox(true); validation.setSuppressDropDownArrow(true); sheet.addValidationData(validation);
    }
}
