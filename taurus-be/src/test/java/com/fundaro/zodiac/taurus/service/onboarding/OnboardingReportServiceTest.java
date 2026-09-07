package com.fundaro.zodiac.taurus.service.onboarding;

import static org.assertj.core.api.Assertions.assertThat;
import com.fundaro.zodiac.taurus.domain.onboarding.OnboardingRowStatus;
import org.junit.jupiter.api.Test;

class OnboardingReportServiceTest {
    @Test void neutralizesSpreadsheetFormulas() {
        assertThat(OnboardingReportService.safe("=HYPERLINK(\"x\")")).startsWith("'=");
        assertThat(OnboardingReportService.safe("@SUM(A1)")).startsWith("'@");
        assertThat(OnboardingReportService.safe("testo")).isEqualTo("testo");
    }

    @Test void translatesRowStatusesInTheReport() {
        assertThat(OnboardingReportService.statusLabel(OnboardingRowStatus.VALID)).isEqualTo("Valida");
        assertThat(OnboardingReportService.statusLabel(OnboardingRowStatus.WARNING)).isEqualTo("Avviso");
        assertThat(OnboardingReportService.statusLabel(OnboardingRowStatus.ERROR)).isEqualTo("Errore");
        assertThat(OnboardingReportService.statusLabel(OnboardingRowStatus.APPLIED)).isEqualTo("Applicata");
        assertThat(OnboardingReportService.statusLabel(OnboardingRowStatus.SKIPPED)).isEqualTo("Saltata");
    }
}
