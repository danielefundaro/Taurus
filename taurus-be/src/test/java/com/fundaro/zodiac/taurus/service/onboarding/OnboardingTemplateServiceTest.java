package com.fundaro.zodiac.taurus.service.onboarding;

import static org.assertj.core.api.Assertions.assertThat;

import com.fundaro.zodiac.taurus.domain.onboarding.OnboardingSection;
import java.io.ByteArrayInputStream;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class OnboardingTemplateServiceTest {
    private final OnboardingTemplateService service = new OnboardingTemplateService();

    @Test
    void createsVersionedWorkbookWithEverySection() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(service.xlsx()))) {
            assertThat(workbook.getSheet("Istruzioni")).isNotNull();
            assertThat(workbook.isSheetHidden(workbook.getSheetIndex("_taurus"))).isTrue();
            assertThat(workbook.getSheet("_taurus").getRow(1).getCell(1).getNumericCellValue()).isEqualTo(1);
            for (OnboardingSection section : OnboardingSection.values()) {
                assertThat(workbook.getSheet(section.getSheetName())).isNotNull();
                assertThat(workbook.getSheet(section.getSheetName()).getRow(0).getLastCellNum()).isEqualTo((short) section.getHeaders().size());
            }
        }
    }

    @Test
    void createsCsvWithContractHeaders() {
        assertThat(new String(service.csv(OnboardingSection.USERS), java.nio.charset.StandardCharsets.UTF_8))
            .isEqualTo("riferimento,nome,cognome,email,data_nascita,ruoli,strumenti,attivo\r\n");
    }
}
