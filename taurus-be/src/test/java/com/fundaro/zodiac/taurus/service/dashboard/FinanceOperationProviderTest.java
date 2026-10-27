package com.fundaro.zodiac.taurus.service.dashboard;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.finance.AccountingYearStatus;
import com.fundaro.zodiac.taurus.domain.finance.FinancialMovementNature;
import com.fundaro.zodiac.taurus.repository.finance.FinancialMovementRepository;
import com.fundaro.zodiac.taurus.repository.projection.FinanceUnreconciledProjection;
import com.fundaro.zodiac.taurus.security.AuthoritiesConstants;
import com.fundaro.zodiac.taurus.service.dto.dashboard.DashboardDomain;
import com.fundaro.zodiac.taurus.service.dto.dashboard.DashboardOperationType;
import com.fundaro.zodiac.taurus.service.dto.dashboard.DashboardSeverity;
import com.fundaro.zodiac.taurus.service.dto.dashboard.OperationalItemDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Movimenti ordinari non riconciliati: chi li vede, quale esercizio conta, quando diventano un avviso e come si
 * presentano gli importi in più valute.
 */
class FinanceOperationProviderTest {

    private final ZonedDateTime now = ZonedDateTime.of(2026, 10, 27, 10, 0, 0, 0, ZoneId.of("Europe/Rome"));
    private FinancialMovementRepository repository;
    private FinanceOperationProvider provider;

    @BeforeEach
    void createProvider() {
        repository = mock(FinancialMovementRepository.class);
        provider = new FinanceOperationProvider(repository, new ApplicationProperties());
    }

    @Test
    void membersArchivistsAndExternalUsersRunNoQuery() {
        for (String authority : List.of(AuthoritiesConstants.USER, AuthoritiesConstants.ARCHIVIST, AuthoritiesConstants.USER_EXTERNAL)) {
            assertThat(provider.getOperations(context(authority))).as(authority).isEmpty();
        }
        verifyNoInteractions(repository);
    }

    @Test
    void theTreasurerSeesTheOrdinaryMovementsOfTheOpenYearsAndOfTheCurrentYear() {
        summaries(summary("EUR", 3, "120.50", LocalDate.of(2026, 10, 20)));

        OperationalItemDTO item = provider.getOperations(context(AuthoritiesConstants.TREASURER)).get(0);

        // Trasferimenti e aperture restano fuori perché si chiede soltanto la natura ORDINARY.
        verify(repository).summarizeUnreconciled(
            FinancialMovementNature.ORDINARY,
            AccountingYearStatus.OPEN,
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 10, 27)
        );
        assertThat(item.type()).isEqualTo(DashboardOperationType.FINANCE_MOVEMENTS_UNRECONCILED);
        assertThat(item.domain()).isEqualTo(DashboardDomain.FINANCE);
        assertThat(item.count()).isEqualTo(3);
        assertThat(item.targetPath()).isEqualTo("/finance?section=movements&reconciled=false");
        assertThat(item.dueAt()).isEqualTo(LocalDate.of(2026, 10, 20).atStartOfDay(now.getZone()));
    }

    @Test
    void neverAddsAmountsOfDifferentCurrencies() {
        summaries(summary("CHF", 1, "10.00", LocalDate.of(2026, 10, 1)), summary("EUR", 2, "1234.56", LocalDate.of(2026, 10, 2)));

        OperationalItemDTO item = provider.getOperations(context(AuthoritiesConstants.ADMIN)).get(0);

        assertThat(item.count()).isEqualTo(3);
        assertThat(item.description()).startsWith("Importo complessivo: ").contains("; ").contains("CHF").contains("€");
        assertThat(item.description()).contains("1.234,56").contains("10,00");
    }

    @Test
    void becomesAWarningOnlyAfterTheConfiguredDays() {
        summaries(summary("EUR", 1, "10.00", now.toLocalDate().minusDays(30)));
        assertThat(provider.getOperations(context(AuthoritiesConstants.ADMIN)).get(0).severity()).isEqualTo(DashboardSeverity.INFO);

        summaries(summary("EUR", 1, "10.00", now.toLocalDate().minusDays(31)));
        assertThat(provider.getOperations(context(AuthoritiesConstants.ADMIN)).get(0).severity()).isEqualTo(DashboardSeverity.WARNING);
    }

    @Test
    void producesNoRowWithoutUnreconciledMovements() {
        summaries();

        assertThat(provider.getOperations(context(AuthoritiesConstants.SUPER_ADMIN))).isEmpty();
    }

    @Test
    void neverExposesDescriptionsOrCounterparties() {
        summaries(summary("EUR", 1, "10.00", LocalDate.of(2026, 10, 1)));

        OperationalItemDTO item = provider.getOperations(context(AuthoritiesConstants.TREASURER)).get(0);

        // La proiezione porta solo valuta, numero, totale e data più vecchia: la riga non può nominare un movimento.
        assertThat(item.title()).isEqualTo("Movimenti da riconciliare");
        assertThat(item.relatedCount()).isNull();
    }

    private void summaries(FinanceUnreconciledProjection... values) {
        when(repository.summarizeUnreconciled(any(), any(), any(), any())).thenReturn(List.of(values));
    }

    private static FinanceUnreconciledProjection summary(String currency, long count, String total, LocalDate oldest) {
        FinanceUnreconciledProjection value = mock(FinanceUnreconciledProjection.class);
        when(value.getCurrency()).thenReturn(currency);
        when(value.getMovementCount()).thenReturn(count);
        when(value.getTotalAmount()).thenReturn(new BigDecimal(total));
        when(value.getOldestBookingDate()).thenReturn(oldest);
        return value;
    }

    private DashboardRequestContext context(String authority) {
        return new DashboardRequestContext("subject", Set.of(authority), now, now.getZone(), mock(AbstractAuthenticationToken.class));
    }
}
