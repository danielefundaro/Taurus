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
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinanceOperationProvider implements DashboardOperationProvider {

    private final FinancialMovementRepository repository;
    private final ApplicationProperties.DashboardProperties properties;

    public FinanceOperationProvider(FinancialMovementRepository repository, ApplicationProperties applicationProperties) {
        this.repository = repository;
        this.properties = applicationProperties.getDashboard();
    }

    @Override
    public DashboardDomain domain() {
        return DashboardDomain.FINANCE;
    }

    @Override
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public List<OperationalItemDTO> getOperations(DashboardRequestContext context) {
        if (!context.hasAnyAuthority(AuthoritiesConstants.SUPER_ADMIN, AuthoritiesConstants.ADMIN, AuthoritiesConstants.TREASURER)) {
            return List.of();
        }
        LocalDate today = context.generatedAt().toLocalDate();
        List<FinanceUnreconciledProjection> summaries = repository.summarizeUnreconciled(
            FinancialMovementNature.ORDINARY,
            AccountingYearStatus.OPEN,
            today.withDayOfYear(1),
            today
        );
        long count = summaries.stream().mapToLong(FinanceUnreconciledProjection::getMovementCount).sum();
        if (count == 0) return List.of();
        LocalDate oldest = summaries.stream()
            .map(FinanceUnreconciledProjection::getOldestBookingDate)
            .filter(java.util.Objects::nonNull)
            .min(LocalDate::compareTo)
            .orElse(today);
        DashboardSeverity severity = oldest.isBefore(today.minusDays(properties.getFinanceUnreconciledWarningDays()))
            ? DashboardSeverity.WARNING
            : DashboardSeverity.INFO;
        String totals = summaries.stream()
            .map(value -> formatAmount(value.getTotalAmount(), value.getCurrency()))
            .collect(java.util.stream.Collectors.joining("; "));
        ZonedDateTime dueAt = oldest.atStartOfDay(context.zoneId());
        return List.of(new OperationalItemDTO(
            DashboardOperationType.FINANCE_MOVEMENTS_UNRECONCILED.name(),
            DashboardOperationType.FINANCE_MOVEMENTS_UNRECONCILED,
            DashboardDomain.FINANCE,
            severity,
            count,
            null,
            "Movimenti da riconciliare",
            "Importo complessivo: " + totals + ".",
            dueAt,
            "Verifica movimenti",
            "/finance?section=movements&reconciled=false"
        ));
    }

    private static String formatAmount(BigDecimal amount, String currencyCode) {
        NumberFormat formatter = NumberFormat.getCurrencyInstance(Locale.ITALY);
        formatter.setCurrency(Currency.getInstance(currencyCode));
        return formatter.format(amount == null ? BigDecimal.ZERO : amount);
    }
}
