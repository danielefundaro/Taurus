package com.fundaro.zodiac.taurus.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.fundaro.zodiac.taurus.aop.notices.NoticesAspect;
import com.fundaro.zodiac.taurus.domain.finance.AccountingYearStatus;
import com.fundaro.zodiac.taurus.domain.finance.FinancialAccount;
import com.fundaro.zodiac.taurus.domain.finance.FinancialAccountType;
import com.fundaro.zodiac.taurus.domain.finance.FinancialCategoryDirection;
import com.fundaro.zodiac.taurus.domain.finance.FinancialDirection;
import com.fundaro.zodiac.taurus.domain.finance.FinancialMovement;
import com.fundaro.zodiac.taurus.domain.finance.FinancialMovementNature;
import com.fundaro.zodiac.taurus.repository.finance.FinancialMovementRepository;
import com.fundaro.zodiac.taurus.service.TenantsService;
import com.fundaro.zodiac.taurus.service.notification.NotificationCommand;

import com.fundaro.zodiac.taurus.service.dto.TenantsDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.AccountDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.AccountStatementDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.AccountYearBalanceDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.CategoryTotalDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.EventEconomicLineDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.YearDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.YearSummaryDTO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.text.PDFTextStripper;
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
    @Mock NotificationOutboxPublisher notificationPublisher;
    @Mock FinanceService financeService;
    @Mock TenantsService tenantsService;
    @Mock TenantLogoLoader tenantLogoLoader;
    private FinanceReportService service;

    @BeforeEach
    void setUp() {
        FinanceReportService target = new FinanceReportService(
            movementRepository,
            financeService,
            new TenantPdfHeaderService(tenantsService, tenantLogoLoader)
        );
        NoticesAspect noticesAspect = new NoticesAspect(notificationPublisher, null, null, null, null, null, null, null, null, null);
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
        ArgumentCaptor<NotificationCommand> notification = ArgumentCaptor.forClass(NotificationCommand.class);
        verify(notificationPublisher).enqueue(notification.capture());
        assertThat(notification.getValue().title()).isEqualTo("Economia: rendiconto esportato");
        assertThat(notification.getValue().message()).contains("Mario Rossi", "01/01/2026–31/12/2026").endsWith(".");
    }

    @Test
    void exportsValidXlsxContainer() {
        var report = service.cashbook(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null, null, null, "xlsx", authentication());

        assertThat(report.bytes()).startsWith((byte) 'P', (byte) 'K');
    }

    @Test
    void exportsPdfDocumentAsARealLandscapeTable() throws Exception {
        when(movementRepository.findAllByDeletedFalseAndBookingDateBetween(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
            .thenReturn(List.of(movement()));

        var report = service.cashbook(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null, null, null, "pdf", authentication());

        assertThat(new String(report.bytes(), 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");
        try (var document = Loader.loadPDF(report.bytes())) {
            assertThat(document.getPage(0).getMediaBox().getWidth()).isGreaterThan(document.getPage(0).getMediaBox().getHeight());
            assertThat(new PDFTextStripper().getText(document))
                .contains("Data", "Conto", "Direzione", "Descrizione estesa del movimento", "125.50", "EUR")
                .doesNotContain("Data | Conto | Direzione");
        }
    }

    @Test
    void exportsPdfWithTheCanonicalTenantHeader() throws Exception {
        JwtAuthenticationToken token = authentication();
        TenantsDTO tenant = new TenantsDTO();
        tenant.setCode("tenant-a");
        tenant.setName("Associazione Musicale Taurus");
        tenant.setAddress("Via Roma 10");
        tenant.setPostalCode("00100");
        tenant.setCity("Roma");
        tenant.setProvince("RM");
        tenant.setCountry("IT");
        tenant.setTaxCode("RSSMRA80A01H501U");
        tenant.setVatNumber("12345678901");
        tenant.setLogoUrl("https://example.test/logo.png");
        when(tenantsService.findByCode("tenant-a", token)).thenReturn(Optional.of(tenant));
        when(tenantLogoLoader.load(tenant.getLogoUrl())).thenReturn(Optional.of(logoPng()));

        var report = service.cashbook(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null, null, null, "pdf", token);

        try (var document = Loader.loadPDF(report.bytes())) {
            assertThat(new PDFTextStripper().getText(document))
                .contains(
                    "Associazione Musicale Taurus",
                    "Sede: Via Roma 10, 00100, Roma, RM, IT",
                    "Codice fiscale: RSSMRA80A01H501U - Partita IVA: 12345678901"
                );
            boolean containsLogo = false;
            for (var name : document.getPage(0).getResources().getXObjectNames()) {
                containsLogo |= document.getPage(0).getResources().getXObject(name) instanceof PDImageXObject;
            }
            assertThat(containsLogo).isTrue();
        }
    }

    @Test
    void exportsTheAnnualReportWithItsSectionsAndTotals() {
        when(financeService.yearSummary(eq(2026), any())).thenReturn(summary());

        var report = service.annual(2026, "csv", authentication());

        String content = new String(report.bytes(), StandardCharsets.UTF_8);
        assertThat(content).contains("Rendiconto annuale 2026", "Conti", "Totali", "Categorie", "Eventi aperti", "Cassa", "Quote");
        assertThat(content).contains("01/01/2026 - 31/12/2026", "tenant-a", "Mario Rossi", "Esercizio: 2026 (Da generare)");
        assertThat(content).doesNotContain("(OPEN)");
    }

    @Test
    void reportsTheExportedDocumentInTheNotification() {
        when(financeService.yearSummary(eq(2026), any())).thenReturn(summary());

        service.annual(2026, "pdf", authentication());

        ArgumentCaptor<NotificationCommand> notification = ArgumentCaptor.forClass(NotificationCommand.class);
        verify(notificationPublisher).enqueue(notification.capture());
        assertThat(notification.getValue().message()).contains("il rendiconto annuale", "PDF", "01/01/2026–31/12/2026").endsWith(".");
    }

    @Test
    void exportsTheCategoryReport() {
        when(financeService.categoryTotals(eq(LocalDate.of(2026, 1, 1)), eq(LocalDate.of(2026, 12, 31)), any()))
            .thenReturn(List.of(new CategoryTotalDTO(5L, "Quote", null, new BigDecimal("80.00"), BigDecimal.ZERO, new BigDecimal("80.00"), 1)));

        var report = service.categories(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), "csv", authentication());

        assertThat(new String(report.bytes(), StandardCharsets.UTF_8)).contains("Rendiconto per categoria", "Quote", "80,00");
    }

    @Test
    void exportsEveryFinancialPdfWithTheSharedTableLayout() throws Exception {
        JwtAuthenticationToken token = authentication();
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 12, 31);
        when(financeService.accountStatement(10L, from, to, token)).thenReturn(statement(from, to));
        when(financeService.eventLines(from, to, token)).thenReturn(List.of(eventLine()));
        when(financeService.categoryTotals(from, to, token)).thenReturn(List.of(categoryTotal()));
        when(financeService.yearSummary(2026, token)).thenReturn(summary());

        assertPdfUsesTable(service.accountStatement(10L, from, to, "pdf", token), "Estratto conto", "Saldo iniziale", "Saldo finale");
        assertPdfUsesTable(service.events(from, to, "pdf", token), "Rendiconto per evento", "Concerto d'estate", "Saldato");
        assertPdfUsesTable(service.categories(from, to, "pdf", token), "Rendiconto per categoria", "Quote associative", "Entrata");
        assertPdfUsesTable(
            service.annual(2026, "pdf", token),
            "Rendiconto annuale 2026",
            "Conti",
            "Totali",
            "Categorie",
            "Eventi aperti",
            "Nessun dato disponibile"
        );
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

    private static AccountStatementDTO statement(LocalDate from, LocalDate to) {
        AccountDTO account = new AccountDTO(
            10L,
            "Cassa",
            null,
            FinancialAccountType.CASH,
            "EUR",
            null,
            null,
            true,
            0,
            new BigDecimal("50.00"),
            1
        );
        return new AccountStatementDTO(
            account,
            from,
            to,
            new BigDecimal("20.00"),
            new BigDecimal("80.00"),
            new BigDecimal("50.00"),
            new BigDecimal("50.00"),
            List.of()
        );
    }

    private static EventEconomicLineDTO eventLine() {
        return new EventEconomicLineDTO(
            20L,
            "Concerto d'estate",
            LocalDate.of(2026, 7, 10),
            new BigDecimal("500.00"),
            new BigDecimal("200.00"),
            new BigDecimal("300.00"),
            new BigDecimal("500.00"),
            new BigDecimal("200.00"),
            new BigDecimal("300.00"),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            "SETTLED"
        );
    }

    private static CategoryTotalDTO categoryTotal() {
        return new CategoryTotalDTO(
            5L,
            "Quote associative",
            FinancialCategoryDirection.INCOME,
            new BigDecimal("80.00"),
            BigDecimal.ZERO,
            new BigDecimal("80.00"),
            1
        );
    }

    private static void assertPdfUsesTable(FinanceReportService.ReportContent report, String... expectedText) throws Exception {
        assertThat(report.mimeType()).isEqualTo("application/pdf");
        try (var document = Loader.loadPDF(report.bytes())) {
            assertThat(new PDFTextStripper().getText(document)).contains(expectedText).doesNotContain(" | ");
        }
    }

    private static FinancialMovement movement() {
        FinancialAccount account = new FinancialAccount();
        account.setId(4L);
        account.setName("Conto corrente principale");
        FinancialMovement movement = new FinancialMovement();
        movement.setId(10L);
        movement.setAccount(account);
        movement.setBookingDate(LocalDate.of(2026, 9, 3));
        movement.setDirection(FinancialDirection.INCOME);
        movement.setNature(FinancialMovementNature.ORDINARY);
        movement.setDescription("Descrizione estesa del movimento per verificare il ritorno a capo nella cella");
        movement.setCounterparty("Associazione di esempio");
        movement.setDocumentReference("DOC-2026-000123");
        movement.setAmount(new BigDecimal("125.50"));
        movement.setCurrency("EUR");
        movement.setReconciled(false);
        return movement;
    }

    private static JwtAuthenticationToken authentication() {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("user-1").claim("tenant", "tenant-a")
            .claim("given_name", "Mario").claim("family_name", "Rossi")
            .issuedAt(now).expiresAt(now.plusSeconds(300)).build();
        return new JwtAuthenticationToken(jwt);
    }

    private static byte[] logoPng() throws Exception {
        BufferedImage image = new BufferedImage(20, 10, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, java.awt.Color.BLUE.getRGB());
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
