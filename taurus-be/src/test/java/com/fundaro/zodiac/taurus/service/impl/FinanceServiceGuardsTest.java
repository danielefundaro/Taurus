package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.CalendarEvents;
import com.fundaro.zodiac.taurus.domain.EventCost;
import com.fundaro.zodiac.taurus.domain.finance.AccountingYear;
import com.fundaro.zodiac.taurus.domain.finance.AccountingYearStatus;
import com.fundaro.zodiac.taurus.domain.finance.FinancialAccount;
import com.fundaro.zodiac.taurus.domain.finance.FinancialAccountType;
import com.fundaro.zodiac.taurus.domain.finance.FinancialCategory;
import com.fundaro.zodiac.taurus.domain.finance.FinancialCategoryDirection;
import com.fundaro.zodiac.taurus.domain.finance.FinancialDirection;
import com.fundaro.zodiac.taurus.domain.finance.FinancialMovement;
import com.fundaro.zodiac.taurus.domain.finance.FinancialMovementAttachment;
import com.fundaro.zodiac.taurus.domain.finance.FinancialMovementNature;
import com.fundaro.zodiac.taurus.repository.CalendarEventsRepository;
import com.fundaro.zodiac.taurus.repository.MediaRepository;
import com.fundaro.zodiac.taurus.repository.finance.AccountingYearRepository;
import com.fundaro.zodiac.taurus.repository.finance.FinancialAccountRepository;
import com.fundaro.zodiac.taurus.repository.finance.FinancialCategoryRepository;
import com.fundaro.zodiac.taurus.repository.finance.FinancialMovementAttachmentRepository;
import com.fundaro.zodiac.taurus.repository.finance.FinancialMovementRepository;
import com.fundaro.zodiac.taurus.service.MediaService;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.EventStatementDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.MovementRequest;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.ReorderRequest;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.TransferRequest;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.YearAnomalyDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.YearSummaryDTO;
import com.fundaro.zodiac.taurus.service.finance.FinanceMetrics;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Le difese del modulo finanza: tipo reale degli allegati, allegati di movimenti eliminati,
 * concorrenza ottimistica dal client, idempotenza dei trasferimenti, segnalazioni dell'esercizio,
 * riporto rigenerabile, filtri della scheda eventi e ordinamento scelto dall'utente.
 */
@ExtendWith(MockitoExtension.class)
class FinanceServiceGuardsTest {

    private static final byte[] PDF = new byte[]{0x25, 0x50, 0x44, 0x46, 0x2D, 0x31};
    private static final byte[] PNG = new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00};

    @Mock
    AccountingYearRepository yearRepository;
    @Mock
    FinancialAccountRepository accountRepository;
    @Mock
    FinancialCategoryRepository categoryRepository;
    @Mock
    FinancialMovementRepository movementRepository;
    @Mock
    FinancialMovementAttachmentRepository attachmentRepository;
    @Mock
    CalendarEventsRepository eventRepository;
    @Mock
    MediaRepository mediaRepository;
    @Mock
    MediaService mediaService;

    private FinanceService service;

    @BeforeEach
    void setUp() {
        service = new FinanceService(
            yearRepository,
            accountRepository,
            categoryRepository,
            movementRepository,
            attachmentRepository,
            eventRepository,
            mediaRepository,
            mediaService,
            new FinanceMetrics(new SimpleMeterRegistry())
        );
    }

    @Test
    void recognisesTheThreeAllowedTypesFromTheirFirstBytes() {
        assertThat(FinanceService.sniffedMime(PDF)).isEqualTo("application/pdf");
        assertThat(FinanceService.sniffedMime(PNG)).isEqualTo("image/png");
        assertThat(FinanceService.sniffedMime(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00})).isEqualTo("image/jpeg");
        assertThat(FinanceService.sniffedMime("MZ\u0090\u0000".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1))).isNull();
        assertThat(FinanceService.sniffedMime(new byte[]{0x25})).isNull();
    }

    @Test
    void refusesAnAttachmentWhoseContentDoesNotMatchTheDeclaredType() throws Exception {
        FinancialMovement movement = movement(30L);
        when(movementRepository.findByIdAndDeletedFalse(30L)).thenReturn(Optional.of(movement));
        MockMultipartFile file = new MockMultipartFile("file", "finta-fattura.pdf", "application/pdf", PNG);

        assertThatThrownBy(() -> service.addAttachment(30L, file, null, authentication()))
            .isInstanceOf(RequestAlertException.class)
            .extracting(error -> ((RequestAlertException) error).getErrorKey())
            .isEqualTo("finance.attachment.contentMismatch");
        verify(mediaService, never()).store(any(), any(), any(), any(), any());
    }

    @Test
    void acceptsAnAttachmentWhoseBytesMatchTheDeclaredType() throws Exception {
        FinancialMovement movement = movement(30L);
        when(movementRepository.findByIdAndDeletedFalse(30L)).thenReturn(Optional.of(movement));
        com.fundaro.zodiac.taurus.service.dto.MediaDTO stored = new com.fundaro.zodiac.taurus.service.dto.MediaDTO();
        stored.setId(44L);
        when(mediaService.store(any(), any(), any(), any(), any())).thenReturn(stored);
        com.fundaro.zodiac.taurus.domain.Media media = new com.fundaro.zodiac.taurus.domain.Media();
        media.setId(44L);
        when(mediaRepository.getReferenceById(44L)).thenReturn(media);
        when(attachmentRepository.save(any(FinancialMovementAttachment.class))).thenAnswer(invocation -> invocation.getArgument(0));
        MockMultipartFile file = new MockMultipartFile("file", "fattura.pdf", "application/pdf", PDF);

        service.addAttachment(30L, file, "Fattura", authentication());

        verify(mediaService).store(any(), any(), any(), any(), any());
    }

    @Test
    void hidesTheAttachmentOfALogicallyDeletedMovement() {
        FinancialMovement movement = movement(30L);
        movement.setDeleted(true);
        FinancialMovementAttachment attachment = new FinancialMovementAttachment();
        attachment.setId(55L);
        attachment.setMovement(movement);
        when(attachmentRepository.findByIdAndDeletedFalseAndActiveTrue(55L)).thenReturn(Optional.of(attachment));

        assertThatThrownBy(() -> service.getAttachment(55L, authentication()))
            .isInstanceOf(RequestAlertException.class)
            .extracting(error -> ((RequestAlertException) error).getErrorKey())
            .isEqualTo("finance.attachment.notFound");
        verify(mediaService, never()).getContent(any(Long.class), any(AbstractAuthenticationToken.class));
    }

    @Test
    void refusesAModificationCarryingAStaleVersion() {
        // L'entità appena letta è alla versione zero: il client che invia una versione diversa
        // sta salvando su una copia vecchia.
        FinancialMovement movement = movement(30L);
        when(movementRepository.findByIdAndDeletedFalse(30L)).thenReturn(Optional.of(movement));

        assertThatThrownBy(() -> service.updateMovement(30L, movementRequest(3L), authentication()))
            .isInstanceOf(RequestAlertException.class)
            .extracting(error -> ((RequestAlertException) error).getErrorKey())
            .isEqualTo("finance.movement.versionConflict");
        verify(movementRepository, never()).save(any(FinancialMovement.class));
    }

    @Test
    void acceptsAModificationCarryingTheCurrentVersion() {
        FinancialMovement movement = movement(30L);
        FinancialAccount account = account(10L, "Cassa");
        when(movementRepository.findByIdAndDeletedFalse(30L)).thenReturn(Optional.of(movement));
        when(accountRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(account));
        when(yearRepository.findByYearAndDeletedFalse(2026)).thenReturn(Optional.of(year(2026, AccountingYearStatus.OPEN)));
        when(movementRepository.save(any(FinancialMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(accountRepository.findAllByDeletedFalseOrderByDisplayOrderAscNameAsc()).thenReturn(List.of(account));
        lenient().when(yearRepository.findAllByDeletedFalseOrderByYearAsc()).thenReturn(List.of());

        assertThat(service.updateMovement(30L, movementRequest(0L), authentication()).description()).isEqualTo("Quota");
    }

    @Test
    void replaysATransferInsteadOfMovingTheMoneyTwice() {
        UUID requestKey = UUID.randomUUID();
        UUID group = UUID.randomUUID();
        AccountingYear year = year(2026, AccountingYearStatus.OPEN);
        FinancialMovement outgoing = transferLeg(1L, group, year, FinancialDirection.EXPENSE);
        FinancialMovement incoming = transferLeg(2L, group, year, FinancialDirection.INCOME);
        outgoing.setRequestKey(requestKey);
        when(movementRepository.findByRequestKeyAndDeletedFalse(requestKey)).thenReturn(Optional.of(outgoing));
        when(movementRepository.findAllByTransferGroupAndDeletedFalse(group)).thenReturn(List.of(outgoing, incoming));

        var replay = service.createTransfer(
            new TransferRequest(10L, 11L, LocalDate.of(2026, 9, 1), null, BigDecimal.TEN, "Giroconto", null, requestKey),
            authentication()
        );

        assertThat(replay.transferGroup()).isEqualTo(group);
        assertThat(replay.outgoing().id()).isEqualTo(1L);
        verify(movementRepository, never()).saveAll(any());
    }

    @Test
    void reportsTheAnomaliesOfAnInconsistentTransferWithoutBlockingTheSummary() {
        AccountingYear year = year(2026, AccountingYearStatus.OPEN);
        UUID group = UUID.randomUUID();
        FinancialMovement outgoing = transferLeg(1L, group, year, FinancialDirection.EXPENSE);
        FinancialMovement incoming = transferLeg(2L, group, year, FinancialDirection.INCOME);
        incoming.setAmount(new BigDecimal("12.00"));
        incoming.setBookingDate(LocalDate.of(2026, 9, 4));
        FinancialMovement ordinary = movement(3L);
        when(yearRepository.findByYearAndDeletedFalse(2026)).thenReturn(Optional.of(year));
        when(movementRepository.findAllByDeletedFalseAndBookingDateBetween(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
            .thenReturn(List.of(outgoing, incoming, ordinary));
        when(accountRepository.findAllByDeletedFalseOrderByDisplayOrderAscNameAsc()).thenReturn(List.of(account(10L, "Cassa")));
        when(attachmentRepository.countByMovement_IdAndDeletedFalseAndActiveTrue(3L)).thenReturn(0L);
        when(eventRepository.findAll()).thenReturn(List.of());

        YearSummaryDTO summary = service.yearSummary(2026, authentication());

        assertThat(summary.anomalies()).extracting(YearAnomalyDTO::code)
            .contains("TRANSFER_AMOUNT_MISMATCH", "TRANSFER_DATE_MISMATCH", "MOVEMENTS_WITHOUT_ATTACHMENT");
        assertThat(summary.ordinaryIncome()).isEqualByComparingTo("10.00");
    }

    @Test
    void regeneratesTheRolloverOfAYearThatWasAlreadyRolledOver() {
        AccountingYear rolledOver = year(2025, AccountingYearStatus.ROLLED_OVER);
        rolledOver.setRolledOverAt(ZonedDateTime.now().minusDays(30));
        AccountingYear next = year(2026, AccountingYearStatus.OPEN);
        FinancialAccount account = account(10L, "Cassa");
        when(yearRepository.findByYearAndDeletedFalse(2025)).thenReturn(Optional.of(rolledOver));
        when(yearRepository.findByYearAndDeletedFalse(2026)).thenReturn(Optional.of(next));
        when(yearRepository.save(any(AccountingYear.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(accountRepository.findAllByDeletedFalseOrderByDisplayOrderAscNameAsc()).thenReturn(List.of(account));
        FinancialMovement booked2025 = movement(3L);
        booked2025.setAccountingYear(rolledOver);
        booked2025.setBookingDate(LocalDate.of(2025, 9, 1));
        when(movementRepository.findAllByDeletedFalseAndBookingDateBetween(any(LocalDate.class), any(LocalDate.class)))
            .thenReturn(List.of(booked2025));
        when(movementRepository.findByAccountingYear_YearAndAccount_IdAndNatureAndDeletedFalse(2026, 10L, FinancialMovementNature.OPENING))
            .thenReturn(Optional.empty());
        when(movementRepository.save(any(FinancialMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.rollover(2025, authentication());

        // Il comando resta efficace: l'apertura del 2026 viene riscritta sul saldo corretto.
        verify(movementRepository).save(any(FinancialMovement.class));
        assertThat(rolledOver.getRolledOverAt()).isAfter(ZonedDateTime.now().minusMinutes(1));
    }

    @Test
    void filtersTheEventsTabByEconomicStatusAndText() {
        CalendarEvents concert = event(70L, "Concerto di Natale", LocalDate.of(2026, 12, 20), "500.00", "100.00");
        CalendarEvents rehearsal = event(71L, "Prova generale", LocalDate.of(2026, 12, 1), null, null);
        when(eventRepository.findAll()).thenReturn(List.of(concert, rehearsal));
        when(movementRepository.findAllByEvent_IdAndDeletedFalseOrderByBookingDateAscIdAsc(anyLong())).thenReturn(List.of());

        var open = service.findEvents(null, null, "NO_MOVEMENTS", null, PageRequest.of(0, 10), authentication());
        var byText = service.findEvents(null, null, null, "natale", PageRequest.of(0, 10), authentication());
        var byPeriod = service.findEvents(LocalDate.of(2026, 12, 10), LocalDate.of(2026, 12, 31), null, null, PageRequest.of(0, 10), authentication());

        assertThat(open.getContent()).extracting(summary -> summary.eventId()).containsExactly(70L);
        assertThat(byText.getContent()).extracting(summary -> summary.eventId()).containsExactly(70L);
        assertThat(byPeriod.getContent()).extracting(summary -> summary.eventId()).containsExactly(70L);
    }

    @Test
    void measuresTheEventAgainstItsBudget() {
        CalendarEvents concert = event(70L, "Concerto di Natale", LocalDate.of(2026, 12, 20), "500.00", "100.00");
        FinancialMovement income = movement(3L);
        income.setAmount(new BigDecimal("450.00"));
        when(eventRepository.findById(70L)).thenReturn(Optional.of(concert));
        when(movementRepository.findAllByEvent_IdAndDeletedFalseOrderByBookingDateAscIdAsc(70L)).thenReturn(List.of(income));

        EventStatementDTO statement = service.eventStatement(70L, authentication());

        assertThat(statement.feeVariance()).isEqualByComparingTo("-50.00");
        assertThat(statement.costVariance()).isEqualByComparingTo("-100.00");
        assertThat(statement.resultVariance()).isEqualByComparingTo("50.00");
    }

    @Test
    void writesTheRequestedOrderOnAccountsAndCategories() {
        FinancialAccount bank = account(11L, "Banca");
        FinancialAccount cash = account(10L, "Cassa");
        when(accountRepository.findByIdAndDeletedFalse(11L)).thenReturn(Optional.of(bank));
        when(accountRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(cash));
        when(accountRepository.findAllByDeletedFalseOrderByDisplayOrderAscNameAsc()).thenReturn(List.of(bank, cash));
        lenient().when(movementRepository.findAllByAccount_IdAndDeletedFalse(anyLong())).thenReturn(List.of());
        when(accountRepository.save(any(FinancialAccount.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.reorderAccounts(new ReorderRequest(List.of(11L, 10L)), authentication());

        assertThat(bank.getDisplayOrder()).isZero();
        assertThat(cash.getDisplayOrder()).isEqualTo(1);
    }

    @Test
    void reordersCategoriesFollowingTheRequestedOrder() {
        FinancialCategory quote = category(20L, "Quote");
        FinancialCategory rimborsi = category(21L, "Rimborsi");
        when(categoryRepository.findByIdAndDeletedFalse(21L)).thenReturn(Optional.of(rimborsi));
        when(categoryRepository.findByIdAndDeletedFalse(20L)).thenReturn(Optional.of(quote));
        when(categoryRepository.findAllByDeletedFalseOrderByDisplayOrderAscNameAsc()).thenReturn(List.of(rimborsi, quote));
        when(categoryRepository.save(any(FinancialCategory.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.reorderCategories(new ReorderRequest(List.of(21L, 20L)), authentication());

        assertThat(rimborsi.getDisplayOrder()).isZero();
        assertThat(quote.getDisplayOrder()).isEqualTo(1);
    }

    @Test
    void keepsOnlyTheTailOfTheIbanForTheLogs() {
        assertThat(FinanceService.maskedIban("IT60X0542811101000000123456")).isEqualTo("****3456");
        assertThat(FinanceService.maskedIban("12")).isEqualTo("****");
        assertThat(FinanceService.maskedIban("   ")).isEqualTo("n/d");
        assertThat(FinanceService.maskedIban(null)).isEqualTo("n/d");
    }

    private static MovementRequest movementRequest(Long version) {
        return new MovementRequest(
            10L,
            null,
            null,
            FinancialDirection.INCOME,
            LocalDate.of(2026, 9, 1),
            null,
            new BigDecimal("10.00"),
            "Quota",
            null,
            null,
            null,
            null,
            version
        );
    }

    private static FinancialAccount account(Long id, String name) {
        FinancialAccount account = new FinancialAccount();
        account.setId(id);
        account.setName(name);
        account.setCurrency("EUR");
        account.setAccountType(FinancialAccountType.CASH);
        account.setActive(true);
        return account;
    }

    private static FinancialCategory category(Long id, String name) {
        FinancialCategory category = new FinancialCategory();
        category.setId(id);
        category.setName(name);
        category.setActive(true);
        category.setDirection(FinancialCategoryDirection.INCOME);
        return category;
    }

    private static AccountingYear year(int value, AccountingYearStatus status) {
        AccountingYear year = new AccountingYear();
        year.setId((long) value);
        year.setYear(value);
        year.setStatus(status);
        year.setStartDate(LocalDate.of(value, 1, 1));
        year.setEndDate(LocalDate.of(value, 12, 31));
        return year;
    }

    private static FinancialMovement movement(Long id) {
        FinancialMovement movement = new FinancialMovement();
        movement.setId(id);
        movement.setAccountingYear(year(2026, AccountingYearStatus.OPEN));
        movement.setAccount(account(10L, "Cassa"));
        movement.setDirection(FinancialDirection.INCOME);
        movement.setNature(FinancialMovementNature.ORDINARY);
        movement.setBookingDate(LocalDate.of(2026, 9, 1));
        movement.setAmount(new BigDecimal("10.00"));
        movement.setCurrency("EUR");
        movement.setDescription("Quota");
        return movement;
    }

    private static FinancialMovement transferLeg(Long id, UUID group, AccountingYear year, FinancialDirection direction) {
        FinancialMovement movement = movement(id);
        movement.setAccountingYear(year);
        movement.setNature(FinancialMovementNature.TRANSFER);
        movement.setTransferGroup(group);
        movement.setDirection(direction);
        movement.setAccount(account(direction == FinancialDirection.EXPENSE ? 10L : 11L, "Conto"));
        movement.setDescription("Giroconto");
        return movement;
    }

    private static CalendarEvents event(Long id, String name, LocalDate date, String fee, String cost) {
        CalendarEvents event = new CalendarEvents();
        event.setId(id);
        event.setName(name);
        event.setStartDate(Date.from(date.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant()));
        if (fee != null) event.setFee(new BigDecimal(fee));
        if (cost != null) {
            EventCost item = new EventCost();
            item.setDescription("Service audio");
            item.setAmount(new BigDecimal(cost));
            event.getCosts().add(item);
        }
        return event;
    }

    private static JwtAuthenticationToken authentication() {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("user-1").claim("tenant", "tenant-a")
            .claim("given_name", "Mario").claim("family_name", "Rossi")
            .issuedAt(now).expiresAt(now.plusSeconds(300)).build();
        return new JwtAuthenticationToken(jwt);
    }
}
