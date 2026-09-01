package com.fundaro.zodiac.taurus.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fundaro.zodiac.taurus.aop.notices.NoticesAspect;
import com.fundaro.zodiac.taurus.domain.CalendarEvents;
import com.fundaro.zodiac.taurus.domain.finance.AccountingYear;
import com.fundaro.zodiac.taurus.domain.finance.AccountingYearStatus;
import com.fundaro.zodiac.taurus.domain.finance.FinancialAccount;
import com.fundaro.zodiac.taurus.domain.finance.FinancialAccountType;
import com.fundaro.zodiac.taurus.domain.finance.FinancialCategory;
import com.fundaro.zodiac.taurus.domain.finance.FinancialCategoryDirection;
import com.fundaro.zodiac.taurus.domain.finance.FinancialDirection;
import com.fundaro.zodiac.taurus.domain.finance.FinancialMovement;
import com.fundaro.zodiac.taurus.domain.finance.FinancialMovementNature;
import com.fundaro.zodiac.taurus.repository.CalendarEventsRepository;
import com.fundaro.zodiac.taurus.repository.MediaRepository;
import com.fundaro.zodiac.taurus.repository.finance.AccountingYearRepository;
import com.fundaro.zodiac.taurus.repository.finance.FinancialAccountRepository;
import com.fundaro.zodiac.taurus.repository.finance.FinancialCategoryRepository;
import com.fundaro.zodiac.taurus.repository.finance.FinancialMovementAttachmentRepository;
import com.fundaro.zodiac.taurus.repository.finance.FinancialMovementRepository;
import com.fundaro.zodiac.taurus.service.MediaService;
import com.fundaro.zodiac.taurus.service.NoticesService;
import com.fundaro.zodiac.taurus.service.NoticesService.FinanceNoticeCommand;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.AccountRequest;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.MovementRequest;
import com.fundaro.zodiac.taurus.service.impl.FinanceNoticeDataService.MovementNotice;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
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
class FinanceServiceTest {

    @Mock AccountingYearRepository yearRepository;
    @Mock FinancialAccountRepository accountRepository;
    @Mock FinancialCategoryRepository categoryRepository;
    @Mock FinancialMovementRepository movementRepository;
    @Mock FinancialMovementAttachmentRepository attachmentRepository;
    @Mock CalendarEventsRepository eventRepository;
    @Mock MediaRepository mediaRepository;
    @Mock MediaService mediaService;
    @Mock FinanceNoticeDataService financeNoticeDataService;
    @Mock NoticesService noticesService;

    private FinanceService service;

    @BeforeEach
    void setUp() {
        FinanceService target = new FinanceService(
            yearRepository,
            accountRepository,
            categoryRepository,
            movementRepository,
            attachmentRepository,
            eventRepository,
            mediaRepository,
            mediaService
        );
        NoticesAspect noticesAspect = new NoticesAspect(
            noticesService,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            financeNoticeDataService
        );
        AspectJProxyFactory proxyFactory = new AspectJProxyFactory(target);
        proxyFactory.addAspect(noticesAspect);
        service = proxyFactory.getProxy();
    }

    @Test
    void createsEditableOrdinaryMovementUsingAccountCurrencyAndBookingYear() {
        FinancialAccount account = account(10L, "Cassa", "EUR");
        when(accountRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(account));
        when(yearRepository.findByYearAndDeletedFalse(2026)).thenReturn(Optional.empty());
        when(yearRepository.save(any(AccountingYear.class))).thenAnswer(invocation -> {
            AccountingYear year = invocation.getArgument(0);
            year.setId(20L);
            return year;
        });
        when(movementRepository.save(any(FinancialMovement.class))).thenAnswer(invocation -> {
            FinancialMovement movement = invocation.getArgument(0);
            movement.setId(30L);
            return movement;
        });
        when(yearRepository.findAllByDeletedFalseOrderByYearAsc()).thenReturn(List.of());

        var result = service.createMovement(
            new MovementRequest(
                10L,
                null,
                null,
                FinancialDirection.INCOME,
                LocalDate.of(2026, 9, 1),
                null,
                new BigDecimal("125.50"),
                "Quota associativa",
                null,
                null,
                null,
                UUID.randomUUID()
            ),
            authentication()
        );

        assertThat(result.nature()).isEqualTo(FinancialMovementNature.ORDINARY);
        assertThat(result.accountingYear()).isEqualTo(2026);
        assertThat(result.currency()).isEqualTo("EUR");
        assertThat(result.amount()).isEqualByComparingTo("125.50");
        ArgumentCaptor<FinanceNoticeCommand> notification = ArgumentCaptor.forClass(FinanceNoticeCommand.class);
        verify(noticesService).enqueueFinanceNotice(notification.capture());
        assertThat(notification.getValue().title()).isEqualTo("Economia: movimento registrato");
        assertThat(notification.getValue().message()).contains("Mario Rossi", "125,50", "01/09/2026").endsWith(".");
    }

    @Test
    void createsOpeningMovementFromInitialAccountBalance() {
        when(accountRepository.existsByNameIgnoreCaseAndDeletedFalseAndActiveTrue("Banca")).thenReturn(false);
        when(accountRepository.save(any(FinancialAccount.class))).thenAnswer(invocation -> {
            FinancialAccount account = invocation.getArgument(0);
            account.setId(10L);
            return account;
        });
        when(yearRepository.findByYearAndDeletedFalse(2026)).thenReturn(Optional.of(year(2026)));
        when(movementRepository.save(any(FinancialMovement.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(movementRepository.findAllByDeletedFalseAndBookingDateBetween(any(LocalDate.class), any(LocalDate.class))).thenReturn(List.of());

        service.createAccount(
            new AccountRequest(
                "Banca",
                "Conto corrente principale",
                FinancialAccountType.BANK,
                "EUR",
                "IT00X0000000000000000000000",
                "Banca di prova",
                1,
                new BigDecimal("250.00"),
                LocalDate.of(2026, 1, 1)
            ),
            authentication()
        );

        var movementCaptor = org.mockito.ArgumentCaptor.forClass(FinancialMovement.class);
        verify(movementRepository).save(movementCaptor.capture());
        assertThat(movementCaptor.getValue().getNature()).isEqualTo(FinancialMovementNature.OPENING);
        assertThat(movementCaptor.getValue().getDirection()).isEqualTo(FinancialDirection.INCOME);
        assertThat(movementCaptor.getValue().getAmount()).isEqualByComparingTo("250.00");
        assertThat(movementCaptor.getValue().getBookingDate()).isEqualTo(LocalDate.of(2026, 1, 1));
    }

    @Test
    void rejectsCategoryWhoseDirectionDoesNotMatchMovement() {
        FinancialAccount account = account(10L, "Cassa", "EUR");
        FinancialCategory category = new FinancialCategory();
        category.setId(11L);
        category.setActive(true);
        category.setDirection(FinancialCategoryDirection.EXPENSE);
        when(accountRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(account));
        when(categoryRepository.findByIdAndDeletedFalse(11L)).thenReturn(Optional.of(category));

        assertThatThrownBy(() -> service.createMovement(
            new MovementRequest(
                10L,
                11L,
                null,
                FinancialDirection.INCOME,
                LocalDate.of(2026, 9, 1),
                null,
                BigDecimal.TEN,
                "Incasso",
                null,
                null,
                null,
                null
            ),
            authentication()
        )).isInstanceOf(RequestAlertException.class);
    }

    @Test
    void deletingOneTransferLegSoftDeletesTheWholePairWithoutRestore() {
        UUID group = UUID.randomUUID();
        AccountingYear year = year(2026);
        FinancialMovement outgoing = movement(1L, group, year, FinancialDirection.EXPENSE);
        FinancialMovement incoming = movement(2L, group, year, FinancialDirection.INCOME);
        when(movementRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(outgoing));
        when(movementRepository.findAllByTransferGroupAndDeletedFalse(group)).thenReturn(List.of(outgoing, incoming));
        when(yearRepository.findAllByDeletedFalseOrderByYearAsc()).thenReturn(List.of(year));
        when(financeNoticeDataService.findMovementGroup(1L)).thenReturn(List.of(
            new MovementNotice(1L, "Cassa", FinancialDirection.EXPENSE, FinancialMovementNature.TRANSFER, LocalDate.of(2026, 9, 1), BigDecimal.TEN, "EUR", "Movimento", group),
            new MovementNotice(2L, "Banca", FinancialDirection.INCOME, FinancialMovementNature.TRANSFER, LocalDate.of(2026, 9, 1), BigDecimal.TEN, "EUR", "Movimento", group)
        ));

        service.deleteMovement(1L, authentication());

        assertThat(outgoing.isDeleted()).isTrue();
        assertThat(incoming.isDeleted()).isTrue();
        verify(movementRepository).saveAll(List.of(outgoing, incoming));
        ArgumentCaptor<FinanceNoticeCommand> notification = ArgumentCaptor.forClass(FinanceNoticeCommand.class);
        verify(noticesService).enqueueFinanceNotice(notification.capture());
        assertThat(notification.getValue().operation()).isEqualTo("TRANSFER_REMOVED");
        assertThat(notification.getValue().title()).isEqualTo("Economia: trasferimento rimosso");
    }

    @Test
    void eventOverrunHasPriorityOverPartialSettlement() {
        CalendarEvents event = new CalendarEvents();
        event.setId(40L);
        event.setName("Concerto");
        event.setFee(new BigDecimal("1000"));
        FinancialMovement income = movement(1L, null, year(2026), FinancialDirection.INCOME);
        income.setAmount(new BigDecimal("1100"));
        income.setEvent(event);
        income.setEventNameSnapshot(event.getName());
        when(eventRepository.findById(40L)).thenReturn(Optional.of(event));
        when(movementRepository.findAllByEvent_IdAndDeletedFalseOrderByBookingDateAscIdAsc(40L)).thenReturn(List.of(income));

        var result = service.eventSummary(40L, authentication());

        assertThat(result.economicStatus()).isEqualTo("OVERPAID_OR_OVERRUN");
        assertThat(result.remainingIncome()).isEqualByComparingTo("-100");
    }

    @Test
    void automaticRolloverUsesEntityFirstWordingAndDeterministicKey() {
        AccountingYear source = year(2026);
        AccountingYear target = year(2027);
        source.setStatus(AccountingYearStatus.OPEN);
        target.setStatus(AccountingYearStatus.OPEN);
        when(financeNoticeDataService.findYearStatus(2026)).thenReturn(AccountingYearStatus.OPEN);
        when(yearRepository.findByYearAndDeletedFalse(2026)).thenReturn(Optional.of(source));
        when(yearRepository.findByYearAndDeletedFalse(2027)).thenReturn(Optional.of(target));
        when(accountRepository.findAllByDeletedFalseOrderByDisplayOrderAscNameAsc()).thenReturn(List.of());
        when(yearRepository.save(any(AccountingYear.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.rolloverForActor(2026, FinanceRolloverScheduler.SYSTEM_ACTOR);

        ArgumentCaptor<FinanceNoticeCommand> notification = ArgumentCaptor.forClass(FinanceNoticeCommand.class);
        verify(noticesService).enqueueFinanceNotice(notification.capture());
        assertThat(notification.getValue().eventKey()).isEqualTo("finance-rollover:2026");
        assertThat(notification.getValue().message()).startsWith("Il riporto dell’esercizio 2026").doesNotContain("Il sistema ha").endsWith(".");
    }

    private static FinancialAccount account(Long id, String name, String currency) {
        FinancialAccount account = new FinancialAccount();
        account.setId(id);
        account.setName(name);
        account.setCurrency(currency);
        account.setAccountType(FinancialAccountType.CASH);
        account.setActive(true);
        return account;
    }

    private static AccountingYear year(int value) {
        AccountingYear year = new AccountingYear();
        year.setId((long) value);
        year.setYear(value);
        year.setStartDate(LocalDate.of(value, 1, 1));
        year.setEndDate(LocalDate.of(value, 12, 31));
        return year;
    }

    private static FinancialMovement movement(Long id, UUID group, AccountingYear year, FinancialDirection direction) {
        FinancialMovement movement = new FinancialMovement();
        movement.setId(id);
        movement.setAccountingYear(year);
        movement.setAccount(account(10L, "Cassa", "EUR"));
        movement.setDirection(direction);
        movement.setNature(group == null ? FinancialMovementNature.ORDINARY : FinancialMovementNature.TRANSFER);
        movement.setTransferGroup(group);
        movement.setBookingDate(LocalDate.of(year.getYear(), 9, 1));
        movement.setAmount(BigDecimal.TEN);
        movement.setCurrency("EUR");
        movement.setDescription("Movimento");
        return movement;
    }

    private static JwtAuthenticationToken authentication() {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("user-1").claim("tenant", "tenant-a")
            .claim("given_name", "Mario").claim("family_name", "Rossi")
            .issuedAt(now).expiresAt(now.plusSeconds(300)).build();
        return new JwtAuthenticationToken(jwt);
    }
}
