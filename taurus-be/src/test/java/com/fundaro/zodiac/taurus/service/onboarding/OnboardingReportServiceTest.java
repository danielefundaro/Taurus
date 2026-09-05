package com.fundaro.zodiac.taurus.service.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;

class OnboardingReportServiceTest {
    @Test void neutralizesSpreadsheetFormulas() {
        assertThat(OnboardingReportService.safe("=HYPERLINK(\"x\")")).startsWith("'=");
        assertThat(OnboardingReportService.safe("@SUM(A1)")).startsWith("'@");
        assertThat(OnboardingReportService.safe("testo")).isEqualTo("testo");
    }
}
