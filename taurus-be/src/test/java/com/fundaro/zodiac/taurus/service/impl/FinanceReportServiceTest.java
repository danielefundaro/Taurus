package com.fundaro.zodiac.taurus.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.fundaro.zodiac.taurus.aop.notices.NoticesAspect;
import com.fundaro.zodiac.taurus.domain.finance.AccountingYearStatus;
import com.fundaro.zodiac.taurus.repository.finance.FinancialMovementRepository;
import com.fundaro.zodiac.taurus.service.NoticesService;
import com.fundaro.zodiac.taurus.service.NoticesService.FinanceNoticeCommand;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.AccountYearBalanceDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.CategoryTotalDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.YearDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.YearSummaryDTO;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class FinanceReportServiceTest {

    @Mock FinancialMovementRepository movementRepository;
    @Mock NoticesService noticesService;
    @Mock FinanceService financeService;
    private FinanceReportService service;

    @BeforeEach
    void setUp() {
        FinanceReportService target = new FinanceReportService(movementRepository, financeService);
        NoticesAspect noticesAspect = new NoticesAspect(noticesService, null, null, null, null, null, null, null, null);
        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(target);
        proxyFactory.addAspect(noticesAspect);
        service = proxyFactory.getProxy();
        lenient()
            .when(movementRepository.findAllByDeletedFalseAndBookingDateBetween(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
            .thenReturn(List.of());
    }

    @Test
    void exportsCsvWithTenantAndPeriod() {
        var report = service.cashbook(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null, null, null, "csv", authentication());

        assertThat(report.mimeType()).startsWith("text/csv");
        assertThat(new String(report.bytes(), StandardCharsets.UTF_8)).contains("tenant-a", "01/01/2026 - 31/12/2026");
        ArgumentCaptor<FinanceNoticeCommand> notification = ArgumentCaptor.forClass(FinanceNoticeCommand.class);
        verify(noticesService).enqueueFinanceNotice(notification.capture());
        assertThat(notification.getValue().title()).isEqualTo("Economia: rendiconto esportato");
        assertThat(notification.getValue().message()).contains("Mario Rossi", "01/01/2026–31/12/2026").endsWith(".");
    }

    @Test
    void exportsValidXlsxContainer() {
        var report = service.cashbook(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null, null, null, "xlsx", authentication());

        assertThat(report.bytes()).startsWith((byte) 'P', (byte) 'K');
    }

    @Test
    void exportsPdfDocument() {
        var report = service.cashbook(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null, null, null, "pdf", authentication());

        assertThat(new String(report.bytes(), 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
    }

    @Test
    void exportsTheAnnualReportWithItsSectionsAndTotals() {
        when(financeService.yearSummary(eq(2026), any())).thenReturn(summary());

        var report = service.annual(2026, "csv", authentication());

        String content = new String(report.bytes(), StandardCharsets.UTF_8);
        assertThat(content).contains("Rendiconto annuale 2026", "Conti", "Totali", "Categorie", "Eventi aperti", "Cassa", "Quote");
        assertThat(content).contains("01/01/2026 - 31/12/2026", "tenant-a", "Mario Rossi");
    }

    @Test
    void reportsTheExportedDocumentInTheNotification() {
        when(financeService.yearSummary(eq(2026), any())).thenReturn(summary());

        service.annual(2026, "pdf", authentication());

        ArgumentCaptor<FinanceNoticeCommand> notification = ArgumentCaptor.forClass(FinanceNoticeCommand.class);
        verify(noticesService).enqueueFinanceNotice(notification.capture());
        assertThat(notification.getValue().message()).contains("il rendiconto annuale", "PDF", "01/01/2026–31/12/2026").endsWith(".");
    }

    @Test
    void exportsTheCategoryReport() {
        when(financeService.categoryTotals(eq(LocalDate.of(2026, 1, 1)), eq(LocalDate.of(2026, 12, 31)), any()))
            .thenReturn(List.of(new CategoryTotalDTO(5L, "Quote", null, new BigDecimal("80.00"), BigDecimal.ZERO, new BigDecimal("80.00"), 1)));

        var report = service.categories(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), "csv", authentication());

        assertThat(new String(report.bytes(), StandardCharsets.UTF_8)).contains("Rendiconto per categoria", "Quote", "80,00");
    }

    private static YearSummaryDTO summary() {
        return new YearSummaryDTO(
            new YearDTO(2026, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), AccountingYearStatus.OPEN, null, null),
            List.of(new AccountYearBalanceDTO(10L, "Cassa", BigDecimal.ZERO, new BigDecimal("80.00"), new BigDecimal("30.00"), new BigDecimal("50.00"))),
            BigDecimal.ZERO,
            new BigDecimal("80.00"),
            new BigDecimal("30.00"),
            new BigDecimal("50.00"),
            new BigDecimal("25.00"),
            new BigDecimal("50.00"),
            List.of(new CategoryTotalDTO(5L, "Quote", null, new BigDecimal("80.00"), BigDecimal.ZERO, new BigDecimal("80.00"), 1)),
            List.of(),
            2,
            new BigDecimal("110.00"),
            null
        );
    }

    private static JwtAuthenticationToken authentication() {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("user-1").claim("tenant", "tenant-a")
            .claim("given_name", "Mario").claim("family_name", "Rossi")
            .issuedAt(now).expiresAt(now.plusSeconds(300)).build();
        return new JwtAuthenticationToken(jwt);
    }
}
