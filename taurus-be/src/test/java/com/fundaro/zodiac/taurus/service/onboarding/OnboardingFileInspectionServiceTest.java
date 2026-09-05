package com.fundaro.zodiac.taurus.service.onboarding;

import static org.assertj.core.api.Assertions.*;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.onboarding.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class OnboardingFileInspectionServiceTest {
    private final OnboardingFileInspectionService service = new OnboardingFileInspectionService(new ApplicationProperties());

    @Test
    void parsesQuotedSemicolonCsv() {
        byte[] csv = "riferimento;nome;descrizione\r\nfiati;Fiati;\"Legni; ottoni\"\r\n".getBytes(StandardCharsets.UTF_8);
        var result = service.inspect(csv, OnboardingImportFormat.CSV, OnboardingSection.INSTRUMENTS, EnumSet.noneOf(OnboardingSection.class));
        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().get(0).values().get("descrizione")).isEqualTo("Legni; ottoni");
    }

    @Test
    void rejectsFormulaCells() throws Exception {
        OnboardingTemplateService templates = new OnboardingTemplateService();
        byte[] bytes;
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(templates.xlsx())); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            var row = workbook.getSheet("Strumenti").createRow(1); row.createCell(0).setCellValue("ref"); row.createCell(1).setCellFormula("1+1"); workbook.write(output); bytes = output.toByteArray();
        }
        assertThatThrownBy(() -> service.inspect(bytes, OnboardingImportFormat.XLSX, null, EnumSet.of(OnboardingSection.INSTRUMENTS)))
            .isInstanceOf(OnboardingFileInspectionService.InspectionException.class).hasMessageContaining("formule");
    }
}
