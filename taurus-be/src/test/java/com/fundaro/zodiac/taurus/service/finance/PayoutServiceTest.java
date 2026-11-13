package com.fundaro.zodiac.taurus.service.finance;

import com.fundaro.zodiac.taurus.domain.CalendarEventPresence;
import com.fundaro.zodiac.taurus.domain.CalendarEvents;
import com.fundaro.zodiac.taurus.domain.EventCost;
import com.fundaro.zodiac.taurus.domain.Users;
import com.fundaro.zodiac.taurus.domain.finance.EventPayoutAdjustment;
import com.fundaro.zodiac.taurus.domain.finance.EventPayoutSettlement;
import com.fundaro.zodiac.taurus.domain.finance.FinancialCategory;
import com.fundaro.zodiac.taurus.domain.finance.FinancialDirection;
import com.fundaro.zodiac.taurus.domain.finance.FinancialMovement;
import com.fundaro.zodiac.taurus.domain.finance.FinancialMovementNature;
import com.fundaro.zodiac.taurus.domain.finance.PayoutSetting;
import com.fundaro.zodiac.taurus.repository.CalendarEventsRepository;
import com.fundaro.zodiac.taurus.repository.UsersRepository;
import com.fundaro.zodiac.taurus.repository.finance.EventPayoutAdjustmentRepository;
import com.fundaro.zodiac.taurus.repository.finance.EventPayoutSettlementRepository;
import com.fundaro.zodiac.taurus.repository.finance.FinancialCategoryRepository;
import com.fundaro.zodiac.taurus.repository.finance.FinancialMovementRepository;
import com.fundaro.zodiac.taurus.repository.finance.PayoutSettingRepository;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.MovementDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.MovementRequest;
import com.fundaro.zodiac.taurus.service.dto.finance.PayoutDtos.AdjustmentRequest;
import com.fundaro.zodiac.taurus.service.dto.finance.PayoutDtos.EventLineDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.PayoutDtos.EventPayoutDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.PayoutDtos.MemberPayoutsDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.PayoutDtos.SettingDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.PayoutDtos.SettingRequest;
import com.fundaro.zodiac.taurus.service.dto.finance.PayoutDtos.SettlementDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.PayoutDtos.SettlementLineRequest;
import com.fundaro.zodiac.taurus.service.dto.finance.PayoutDtos.SettlementRequest;
import com.fundaro.zodiac.taurus.service.impl.FinanceService;
import com.fundaro.zodiac.taurus.service.impl.NotificationOutboxPublisher;
import com.fundaro.zodiac.taurus.service.notification.NotificationCommand;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The shares service on the examples of docs/event-payout-shares-spec.md: actual and simulated shares, factors and fixed
 * payments, dated parameters and settlements.
 */
@ExtendWith(MockitoExtension.class)
class PayoutServiceTest {

    private static final LocalDate EVENT_DATE = LocalDate.of(2026, 3, 15);

    @Mock
    PayoutSettingRepository settingRepository;
    @Mock
    EventPayoutAdjustmentRepository adjustmentRepository;
    @Mock
    EventPayoutSettlementRepository settlementRepository;
    @Mock
    CalendarEventsRepository eventRepository;
    @Mock
    UsersRepository userRepository;
    @Mock
    FinancialMovementRepository movementRepository;
    @Mock
    FinancialCategoryRepository categoryRepository;
    @Mock
    FinanceService financeService;
    @Mock
    NotificationOutboxPublisher notificationPublisher;

    private PayoutService service;

    private final Users anna = user(1L, "Anna", "Bianchi");
    private final Users bruno = user(2L, "Bruno", "Conti");
    private final Users carla = user(3L, "Carla", "Danti");
    private final Users dario = user(4L, "Dario", "Esposito");
    private final Users elena = user(5L, "Elena", "Ferri");
    private final List<Users> everybody = List.of(anna, bruno, carla, dario, elena);

    private final List<PayoutSetting> settings = new ArrayList<>();
    private final List<FinancialMovement> movements = new ArrayList<>();
    private final List<EventPayoutAdjustment> adjustments = new ArrayList<>();
    private final List<EventPayoutSettlement> settlements = new ArrayList<>();
    private CalendarEvents concert;

    @BeforeEach
    void setUp() {
        service = new PayoutService(
            settingRepository,
            adjustmentRepository,
            settlementRepository,
            eventRepository,
            userRepository,
            movementRepository,
            categoryRepository,
            financeService
        );
        service.setNotificationPublisher(notificationPublisher);
        settings.add(setting(10L, LocalDate.of(2026, 1, 1), "20", "5"));
        concert = event(70L, "Concerto di primavera", EVENT_DATE, "1000", "120", everybody);

        lenient().when(settingRepository.findAllByDeletedFalseOrderByValidFromAsc()).thenAnswer(invocation -> List.copyOf(settings));
        lenient().when(eventRepository.findByIdAndDeletedFalse(70L)).thenAnswer(invocation -> Optional.of(concert));
        lenient().when(movementRepository.findAllByEvent_IdInAndDeletedFalseOrderByBookingDateAscIdAsc(anyCollection()))
            .thenAnswer(invocation -> List.copyOf(movements));
        lenient().when(settlementRepository.findSettlingMovementIds(anyCollection())).thenAnswer(invocation -> {
            Collection<Long> ids = invocation.getArgument(0);
            return settlements.stream()
                .filter(settlement -> !settlement.isDeleted() && ids.contains(settlement.getMovementId()))
                .map(EventPayoutSettlement::getMovementId)
                .collect(java.util.stream.Collectors.toSet());
        });
        lenient().when(settlementRepository.findAllByEventIdInAndDeletedFalse(anyCollection()))
            .thenAnswer(invocation -> settlements.stream().filter(settlement -> !settlement.isDeleted()).toList());
        lenient().when(adjustmentRepository.findAllByEventIdInAndDeletedFalse(anyCollection()))
            .thenAnswer(invocation -> adjustments.stream().filter(adjustment -> !adjustment.isDeleted()).toList());
        lenient().when(userRepository.findAllById(any())).thenReturn(everybody);
        everybody.forEach(user -> lenient().when(userRepository.findByIdAndDeletedFalse(user.getId())).thenReturn(Optional.of(user)));
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Simulation

    @Test
    void simulatesThePlannedAndTheActualSharesOfTheExample() {
        factor(elena, "0.5", "Presente a metà serata");
        movement(1L, FinancialDirection.INCOME, "1000");
        movement(2L, FinancialDirection.EXPENSE, "120");

        EventPayoutDTO result = service.simulate(70L, null, null, authentication());

        assertThat(result.preview()).isFalse();
        assertThat(result.incomeRecorded()).isTrue();
        assertThat(result.sharePercent()).isEqualByComparingTo("20");
        assertThat(result.planned().residual()).isEqualByComparingTo("75");
        assertThat(result.actual().effectiveAssociationShare()).isEqualByComparingTo("275");
        assertThat(result.lines()).extracting(EventLineDTO::name)
            .containsExactly("Anna Bianchi", "Bruno Conti", "Carla Danti", "Dario Esposito", "Elena Ferri");
        assertThat(result.lines()).extracting(EventLineDTO::realShare)
            .usingElementComparator(BigDecimal::compareTo)
            .containsExactly(amount("135"), amount("135"), amount("135"), amount("135"), amount("65"));
        assertThat(result.lines().get(4).reason()).isEqualTo("Presente a metà serata");
        assertThat(result.dueTotal()).isEqualByComparingTo("605");
    }

    @Test
    void keepsTheSettlementsOutOfTheExpensesOfTheEvent() {
        movement(1L, FinancialDirection.INCOME, "1000");
        movement(2L, FinancialDirection.EXPENSE, "120");
        EventPayoutDTO before = service.simulate(70L, null, null, authentication());

        movement(3L, FinancialDirection.EXPENSE, "135");
        settlement(3L, anna, "135");
        EventPayoutDTO after = service.simulate(70L, null, null, authentication());

        assertThat(after.actual().expenses()).isEqualByComparingTo(before.actual().expenses());
        assertThat(after.lines()).extracting(EventLineDTO::realShare)
            .usingElementComparator(BigDecimal::compareTo)
            .containsExactlyElementsOf(before.lines().stream().map(EventLineDTO::realShare).toList());
        EventLineDTO paid = after.lines().getFirst();
        assertThat(paid.settled()).isEqualByComparingTo("135");
        assertThat(paid.due()).isEqualByComparingTo("0");
        assertThat(after.settledTotal()).isEqualByComparingTo("135");
    }

    @Test
    void keepsTheActualShareAtZeroUntilTheIncomeIsRecorded() {
        fixed(elena, "180", "Solista ingaggiato");

        EventPayoutDTO result = service.simulate(70L, null, null, authentication());

        assertThat(result.incomeRecorded()).isFalse();
        EventLineDTO first = result.lines().getFirst();
        assertThat(first.simulatedShare()).isEqualByComparingTo("125");
        assertThat(first.realShare()).isEqualByComparingTo("0");
        EventLineDTO soloist = result.lines().getLast();
        assertThat(soloist.kind()).isEqualTo("FIXED");
        assertThat(soloist.simulatedShare()).isEqualByComparingTo("180");
        assertThat(soloist.realShare()).isEqualByComparingTo("180");
        assertThat(soloist.due()).isEqualByComparingTo("180");
        assertThat(result.actual().deficit()).isEqualByComparingTo("180");
    }

    @Test
    void exposesTheDeviationWhenTheActualShareFallsBelowWhatWasPaid() {
        movement(1L, FinancialDirection.INCOME, "500");
        movement(2L, FinancialDirection.EXPENSE, "120");
        movement(3L, FinancialDirection.EXPENSE, "135");
        settlement(3L, anna, "135");

        EventPayoutDTO result = service.simulate(70L, null, null, authentication());

        EventLineDTO paid = result.lines().getFirst();
        assertThat(paid.realShare()).isEqualByComparingTo("55");
        assertThat(paid.due()).isEqualByComparingTo("0");
        assertThat(paid.deviation()).isEqualByComparingTo("80");
        assertThat(result.deviationTotal()).isEqualByComparingTo("80");
    }

    @Test
    void keepsALineForAPaidUserWhoIsNoLongerPresent() {
        movement(1L, FinancialDirection.INCOME, "1000");
        concert.getPresences().removeIf(presence -> presence.getUser() == elena);
        movement(3L, FinancialDirection.EXPENSE, "100");
        settlement(3L, elena, "100");

        EventPayoutDTO result = service.simulate(70L, null, null, authentication());

        EventLineDTO former = result.lines().getLast();
        assertThat(former.name()).isEqualTo("Elena Ferri");
        assertThat(former.present()).isFalse();
        assertThat(former.deviation()).isEqualByComparingTo("100");
    }

    @Test
    void refusesTheSimulationWithoutParametersForTheDateOfTheEvent() {
        settings.clear();
        settings.add(setting(10L, EVENT_DATE.plusDays(1), "20", "5"));

        assertError(() -> service.simulate(70L, null, null, authentication()), "payout.setting.notConfigured");
    }

    @Test
    void appliesTheVersionInForceAtTheDateOfTheEventAlsoWhenAddedLater() {
        settings.add(setting(11L, LocalDate.of(2026, 6, 1), "30", "1"));
        assertThat(service.simulate(70L, null, null, authentication()).settingId()).isEqualTo(10L);

        // A retroactive version between the first one and the event takes over.
        settings.add(setting(12L, LocalDate.of(2026, 2, 1), "10", "2"));
        EventPayoutDTO result = service.simulate(70L, null, null, authentication());
        assertThat(result.settingId()).isEqualTo(12L);
        assertThat(result.sharePercent()).isEqualByComparingTo("10");
    }

    @Test
    void previewsOtherParametersWithoutSavingThem() {
        EventPayoutDTO result = service.simulate(70L, BigDecimal.ZERO, new BigDecimal("10"), authentication());

        assertThat(result.preview()).isTrue();
        assertThat(result.sharePercent()).isEqualByComparingTo("0");
        assertThat(result.savedSharePercent()).isEqualByComparingTo("20");
        assertThat(result.planned().baseShare()).isEqualByComparingTo("176");
        assertThat(result.lines().getFirst().simulatedShare()).isEqualByComparingTo("170");
        verify(settingRepository, never()).save(any());
        assertError(() -> service.simulate(70L, new BigDecimal("101"), null, authentication()), "payout.preview.invalid");
        assertError(() -> service.simulate(70L, null, new BigDecimal("-1"), authentication()), "payout.preview.invalid");
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Factors and fixed payments

    @Test
    void refusesAnAdjustmentWithoutReasonOrValue() {
        assertError(() -> service.setAdjustment(70L, 1L, new AdjustmentRequest(new BigDecimal("0.5"), null, " "), authentication()),
            "payout.adjustment.reasonRequired");
        assertError(() -> service.setAdjustment(70L, 1L, new AdjustmentRequest(null, null, "Motivo"), authentication()),
            "payout.adjustment.valueRequired");
        assertError(() -> service.setAdjustment(70L, 1L, new AdjustmentRequest(new BigDecimal("0.5"), new BigDecimal("10"), "Motivo"), authentication()),
            "payout.adjustment.valueRequired");
        assertError(() -> service.setAdjustment(70L, 1L, new AdjustmentRequest(new BigDecimal("1.5"), null, "Motivo"), authentication()),
            "payout.adjustment.factorOutOfRange");
        assertError(() -> service.setAdjustment(70L, 1L, new AdjustmentRequest(new BigDecimal("-0.1"), null, "Motivo"), authentication()),
            "payout.adjustment.factorOutOfRange");
        assertError(() -> service.setAdjustment(70L, 1L, new AdjustmentRequest(null, BigDecimal.ZERO, "Motivo"), authentication()),
            "payout.adjustment.fixedAmountNotPositive");
        verify(adjustmentRepository, never()).save(any());
    }

    @Test
    void refusesAnAdjustmentForAUserWhoIsNotPresent() {
        Users guest = user(9L, "Gino", "Ospite");
        when(userRepository.findByIdAndDeletedFalse(9L)).thenReturn(Optional.of(guest));

        assertError(() -> service.setAdjustment(70L, 9L, new AdjustmentRequest(new BigDecimal("0.5"), null, "Motivo"), authentication()),
            "payout.adjustment.userNotPresent");
    }

    @Test
    void assignsAFactorWithItsReasonAndNotifiesIt() {
        when(adjustmentRepository.findByEventIdAndUserIdAndDeletedFalse(70L, 5L)).thenReturn(Optional.empty());

        service.setAdjustment(70L, 5L, new AdjustmentRequest(new BigDecimal("0.5"), null, " Presente a metà serata "), authentication());

        ArgumentCaptor<EventPayoutAdjustment> saved = ArgumentCaptor.forClass(EventPayoutAdjustment.class);
        verify(adjustmentRepository).save(saved.capture());
        assertThat(saved.getValue().getFactor()).isEqualByComparingTo("0.5");
        assertThat(saved.getValue().getFixedAmount()).isNull();
        assertThat(saved.getValue().getReason()).isEqualTo("Presente a metà serata");
        assertThat(saved.getValue().getInsertBy()).isEqualTo("user-1");
        NotificationCommand notice = notice();
        assertThat(notice.operation()).isEqualTo("PAYOUT_ADJUSTMENT_SET");
        assertThat(notice.aggregateType()).isEqualTo("EVENT_PAYOUT");
        assertThat(notice.title()).isEqualTo("Economia: fattore assegnato");
        assertThat(notice.message()).isEqualTo(
            "Mario Rossi ha assegnato a Elena Ferri il fattore 0,5 per l'evento “Concerto di primavera” del 15/03/2026. "
                + "Motivazione: Presente a metà serata."
        );
    }

    @Test
    void replacesAFactorWithAFixedPayment() {
        EventPayoutAdjustment existing = factor(elena, "0.5", "Presente a metà serata");
        when(adjustmentRepository.findByEventIdAndUserIdAndDeletedFalse(70L, 5L)).thenReturn(Optional.of(existing));

        service.setAdjustment(70L, 5L, new AdjustmentRequest(null, new BigDecimal("180"), "Solista ingaggiato"), authentication());

        assertThat(existing.getFactor()).isNull();
        assertThat(existing.getFixedAmount()).isEqualByComparingTo("180");
        assertThat(notice().message()).isEqualTo(
            "Mario Rossi ha assegnato a Elena Ferri un pagamento fisso di 180,00 € per l'evento “Concerto di primavera” del 15/03/2026. "
                + "Motivazione: Solista ingaggiato."
        );
    }

    @Test
    void treatsAFactorOfOneAsTheBaseShare() {
        EventPayoutAdjustment existing = factor(elena, "0.5", "Presente a metà serata");
        when(adjustmentRepository.findByEventIdAndUserIdAndDeletedFalse(70L, 5L)).thenReturn(Optional.of(existing));

        service.setAdjustment(70L, 5L, new AdjustmentRequest(BigDecimal.ONE, null, "Presente tutta la sera"), authentication());

        assertThat(existing.isDeleted()).isTrue();
        assertThat(notice().operation()).isEqualTo("PAYOUT_ADJUSTMENT_REMOVED");
    }

    @Test
    void removesAnAdjustmentOnlyWhenThereIsOne() {
        when(adjustmentRepository.findByEventIdAndUserIdAndDeletedFalse(70L, 5L)).thenReturn(Optional.empty());
        service.removeAdjustment(70L, 5L, authentication());
        verify(notificationPublisher, never()).enqueue(any());

        EventPayoutAdjustment existing = fixed(elena, "180", "Solista ingaggiato");
        when(adjustmentRepository.findByEventIdAndUserIdAndDeletedFalse(70L, 5L)).thenReturn(Optional.of(existing));
        service.removeAdjustment(70L, 5L, authentication());

        assertThat(existing.isDeleted()).isTrue();
        assertThat(notice().message()).isEqualTo(
            "Mario Rossi ha rimosso il pagamento fisso di 180,00 € di Elena Ferri per l'evento “Concerto di primavera” del 15/03/2026."
        );
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Settlement

    @Test
    void refusesAnEmptySettlementOrOneForMoreMembers() {
        assertError(() -> service.settle(settlementRequest(List.of()), authentication()), "payout.settlement.empty");
        assertError(() -> service.settle(settlementRequest(null), authentication()), "payout.settlement.empty");
        assertError(() -> service.settle(settlementRequest(List.of(
            new SettlementLineRequest(70L, 1L, new BigDecimal("10")),
            new SettlementLineRequest(70L, 2L, new BigDecimal("10"))
        )), authentication()), "payout.settlement.multipleUsers");
        verify(financeService, never()).createPayoutMovement(any(), anyString());
    }

    @Test
    void refusesToPayMoreThanTheActualShareStillDue() {
        movement(1L, FinancialDirection.INCOME, "1000");
        movement(2L, FinancialDirection.EXPENSE, "120");

        assertError(() -> service.settle(settlementRequest(List.of(new SettlementLineRequest(70L, 1L, new BigDecimal("140")))), authentication()),
            "payout.settlement.amountExceedsDue");
        // The planned share is not payable: without income there is nothing due.
        movements.clear();
        assertError(() -> service.settle(settlementRequest(List.of(new SettlementLineRequest(70L, 1L, new BigDecimal("1")))), authentication()),
            "payout.settlement.amountExceedsDue");
        verify(financeService, never()).createPayoutMovement(any(), anyString());
    }

    @Test
    void settlesOneMemberWithOneExpenseMovementAndLeavesTheRestDue() {
        movement(1L, FinancialDirection.INCOME, "1000");
        movement(2L, FinancialDirection.EXPENSE, "120");
        FinancialCategory category = new FinancialCategory();
        category.setId(140L);
        when(categoryRepository.findFirstByNameIgnoreCaseAndDeletedFalseAndActiveTrueOrderByIdAsc("Compensi ai presenti"))
            .thenReturn(Optional.of(category));
        when(financeService.createPayoutMovement(any(), eq("user-1"))).thenReturn(movementDto(90L, "100.00"));
        when(settlementRepository.saveAll(any())).thenAnswer(invocation -> {
            List<EventPayoutSettlement> lines = invocation.getArgument(0);
            settlements.addAll(lines);
            return lines;
        });

        SettlementDTO result = service.settle(settlementRequest(List.of(
            new SettlementLineRequest(70L, 1L, new BigDecimal("60")),
            new SettlementLineRequest(70L, 1L, new BigDecimal("40"))
        )), authentication());

        ArgumentCaptor<MovementRequest> request = ArgumentCaptor.forClass(MovementRequest.class);
        verify(financeService).createPayoutMovement(request.capture(), eq("user-1"));
        assertThat(request.getValue().direction()).isEqualTo(FinancialDirection.EXPENSE);
        assertThat(request.getValue().amount()).isEqualByComparingTo("100");
        assertThat(request.getValue().eventId()).isEqualTo(70L);
        assertThat(request.getValue().categoryId()).isEqualTo(140L);
        assertThat(request.getValue().counterparty()).isEqualTo("Anna Bianchi");
        assertThat(request.getValue().description()).isEqualTo("Compenso per l'evento “Concerto di primavera” del 15/03/2026");
        assertThat(result.lines()).singleElement().satisfies(line -> {
            assertThat(line.eventId()).isEqualTo(70L);
            assertThat(line.amount()).isEqualByComparingTo("100");
        });
        assertThat(notice().message()).isEqualTo(
            "Mario Rossi ha liquidato a Anna Bianchi 100,00 € per l'evento “Concerto di primavera” del 15/03/2026."
        );

        // The movement of the settlement is linked to the event, but it is not one of its expenses.
        movement(90L, FinancialDirection.EXPENSE, "100");
        EventLineDTO anna = service.simulate(70L, null, null, authentication()).lines().getFirst();
        assertThat(anna.realShare()).isEqualByComparingTo("135");
        assertThat(anna.settled()).isEqualByComparingTo("100");
        assertThat(anna.due()).isEqualByComparingTo("35");
    }

    @Test
    void settlesAFixedPaymentBeforeTheIncome() {
        fixed(elena, "180", "Solista ingaggiato");
        when(financeService.createPayoutMovement(any(), eq("user-1"))).thenReturn(movementDto(91L, "180.00"));

        service.settle(settlementRequest(List.of(new SettlementLineRequest(70L, 5L, new BigDecimal("180")))), authentication());

        verify(financeService).createPayoutMovement(any(), eq("user-1"));
        verify(settlementRepository).saveAll(any());
    }

    @Test
    void describesASettlementOfMoreEventsWithoutLinkingItToOne() {
        CalendarEvents rehearsal = event(71L, "Serata danzante", EVENT_DATE.plusDays(7), "400", null, List.of(anna, bruno));
        when(eventRepository.findByIdAndDeletedFalse(71L)).thenReturn(Optional.of(rehearsal));
        FinancialMovement concertIncome = movement(1L, FinancialDirection.INCOME, "1000");
        FinancialMovement dinnerIncome = movement(4L, FinancialDirection.INCOME, "400");
        dinnerIncome.setEvent(rehearsal);
        assertThat(concertIncome.getEvent()).isSameAs(concert);
        when(financeService.createPayoutMovement(any(), eq("user-1"))).thenReturn(movementDto(92L, "200.00"));

        service.settle(settlementRequest(List.of(
            new SettlementLineRequest(70L, 1L, new BigDecimal("100")),
            new SettlementLineRequest(71L, 1L, new BigDecimal("100"))
        )), authentication());

        ArgumentCaptor<MovementRequest> request = ArgumentCaptor.forClass(MovementRequest.class);
        verify(financeService).createPayoutMovement(request.capture(), eq("user-1"));
        assertThat(request.getValue().eventId()).isNull();
        assertThat(request.getValue().description()).isEqualTo("Compensi per 2 eventi");
        assertThat(notice().message()).isEqualTo("Mario Rossi ha liquidato a Anna Bianchi 200,00 € per 2 eventi.");
    }

    @Test
    void returnsTheSettlementAlreadyRegisteredForARepeatedRequest() {
        UUID requestKey = UUID.randomUUID();
        FinancialMovement registered = movement(93L, FinancialDirection.EXPENSE, "100");
        registered.setBookingDate(EVENT_DATE);
        EventPayoutSettlement line = settlement(93L, anna, "100");
        when(movementRepository.findByRequestKeyAndDeletedFalse(requestKey)).thenReturn(Optional.of(registered));
        when(settlementRepository.findAllByMovementIdAndDeletedFalse(93L)).thenReturn(List.of(line));
        when(userRepository.findById(1L)).thenReturn(Optional.of(anna));
        SettlementRequest request = new SettlementRequest(10L, null, EVENT_DATE, null, null, requestKey,
            List.of(new SettlementLineRequest(70L, 1L, new BigDecimal("100"))));

        SettlementDTO result = service.settle(request, authentication());

        assertThat(result.movementId()).isEqualTo(93L);
        verify(financeService, never()).createPayoutMovement(any(), anyString());
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Members

    @Test
    void listsTheSharesOfAMemberWithWhatIsDue() {
        movement(1L, FinancialDirection.INCOME, "1000");
        movement(2L, FinancialDirection.EXPENSE, "120");
        movement(3L, FinancialDirection.EXPENSE, "100");
        settlement(3L, anna, "100");
        when(eventRepository.findAllWithPresentUser(1L)).thenReturn(List.of(concert));
        when(settlementRepository.findAllByUserIdAndDeletedFalse(1L)).thenReturn(List.copyOf(settlements));

        MemberPayoutsDTO all = service.findMember(1L, null, null, null, authentication());
        assertThat(all.name()).isEqualTo("Anna Bianchi");
        assertThat(all.lines()).singleElement().satisfies(line -> {
            assertThat(line.configured()).isTrue();
            assertThat(line.realShare()).isEqualByComparingTo("135");
            assertThat(line.due()).isEqualByComparingTo("35");
        });
        assertThat(all.dueTotal()).isEqualByComparingTo("35");

        assertThat(service.findMember(1L, null, null, "SETTLED", authentication()).lines()).isEmpty();
        assertThat(service.findMember(1L, null, null, "DUE", authentication()).lines()).hasSize(1);
        assertThat(service.findMember(1L, EVENT_DATE.plusDays(1), null, null, authentication()).lines()).isEmpty();
    }

    @Test
    void keepsTheFixedPaymentsDueOnAnEventWithoutParameters() {
        settings.clear();
        fixed(elena, "180", "Solista ingaggiato");
        when(eventRepository.findAllWithPresentUser(5L)).thenReturn(List.of(concert));
        when(settlementRepository.findAllByUserIdAndDeletedFalse(5L)).thenReturn(List.of());

        MemberPayoutsDTO result = service.findMember(5L, null, null, null, authentication());

        assertThat(result.lines()).singleElement().satisfies(line -> {
            assertThat(line.configured()).isFalse();
            assertThat(line.kind()).isEqualTo("FIXED");
            assertThat(line.due()).isEqualByComparingTo("180");
        });
    }

    @Test
    void summarisesTheMembersWithShares() {
        movement(1L, FinancialDirection.INCOME, "1000");
        movement(2L, FinancialDirection.EXPENSE, "120");
        when(eventRepository.findAllWithPresences()).thenReturn(List.of(concert));

        assertThat(service.findMembers(null, null, authentication()))
            .hasSize(5)
            .first()
            .satisfies(member -> {
                assertThat(member.name()).isEqualTo("Anna Bianchi");
                assertThat(member.dueTotal()).isEqualByComparingTo("135");
            });
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Dated parameters

    @Test
    void refusesTwoVersionsWithTheSameDate() {
        when(settingRepository.existsByValidFromAndDeletedFalse(LocalDate.of(2026, 1, 1))).thenReturn(true);

        assertError(() -> service.createSetting(new SettingRequest(LocalDate.of(2026, 1, 1), BigDecimal.TEN, BigDecimal.ONE), authentication()),
            "payout.setting.dateExists");
    }

    @Test
    void namesTheAuthorsOfTheVersionsInsteadOfTheirIdentityIds() {
        settings.getFirst().setInsertBy("kc-anna");
        settings.add(setting(11L, LocalDate.of(2026, 6, 1), "15", "5"));
        settings.getLast().setInsertBy("kc-bruno");
        settings.add(setting(12L, LocalDate.of(2026, 9, 1), "10", "5"));
        settings.getLast().setInsertBy("kc-gone");
        settings.add(setting(13L, LocalDate.of(2026, 10, 1), "10", "5"));
        anna.setKeycloakId("kc-anna");
        bruno.setKeycloakId("kc-bruno");
        // An erased row with the same identity as Bruno does not hide the active member.
        Users erased = user(99L, "Utente", "rimosso");
        erased.setKeycloakId("kc-bruno");
        erased.setDeleted(true);
        when(userRepository.findAllByKeycloakIdIn(Set.of("kc-anna", "kc-bruno", "kc-gone"))).thenReturn(List.of(erased, anna, bruno));

        List<SettingDTO> result = service.findSettings(authentication());

        assertThat(result).extracting(SettingDTO::createdByName).containsExactly("Anna Bianchi", "Bruno Conti", null, null);
    }

    @Test
    void createsAVersionAndNotifiesIt() {
        when(settingRepository.save(any(PayoutSetting.class))).thenAnswer(invocation -> invocation.getArgument(0));
        anna.setKeycloakId("user-1");
        when(userRepository.findAllByKeycloakIdIn(Set.of("user-1"))).thenReturn(List.of(anna));

        SettingDTO created = service.createSetting(new SettingRequest(LocalDate.of(2026, 9, 1), new BigDecimal("12.5"), new BigDecimal("5")), authentication());

        NotificationCommand notice = notice();
        assertThat(created.createdByName()).isEqualTo("Anna Bianchi");
        assertThat(notice.operation()).isEqualTo("PAYOUT_SETTING_CREATED");
        assertThat(notice.title()).isEqualTo("Economia: parametri dei compensi creati");
        assertThat(notice.message()).isEqualTo(
            "Mario Rossi ha creato i parametri dei compensi con decorrenza 01/09/2026: quota di cassa 12,5%, passo 5,00 €."
        );
    }

    @Test
    void keepsTheOnlyVersionOfEventsWithPresentUsers() {
        PayoutSetting only = settings.getFirst();
        when(settingRepository.findByIdAndDeletedFalse(10L)).thenReturn(Optional.of(only));
        when(eventRepository.countWithPresencesBetween(any(Date.class), any(Date.class))).thenReturn(1L);

        assertError(() -> service.deleteSetting(10L, authentication()), "payout.setting.inUse");
        assertError(() -> service.updateSetting(10L, new SettingRequest(LocalDate.of(2026, 4, 1), BigDecimal.TEN, BigDecimal.ONE), authentication()),
            "payout.setting.inUse");
        assertThat(only.isDeleted()).isFalse();

        // With an earlier version the events fall back on it.
        settings.add(setting(9L, LocalDate.of(2025, 1, 1), "15", "5"));
        service.deleteSetting(10L, authentication());
        assertThat(only.isDeleted()).isTrue();
        assertThat(notice().operation()).isEqualTo("PAYOUT_SETTING_REMOVED");
    }

    // ---------------------------------------------------------------------------------------------------------------

    private static void assertError(ThrowingCallable call, String key) {
        assertThatThrownBy(call)
            .isInstanceOf(RequestAlertException.class)
            .extracting(error -> ((RequestAlertException) error).getErrorKey())
            .isEqualTo(key);
    }

    private NotificationCommand notice() {
        ArgumentCaptor<NotificationCommand> captor = ArgumentCaptor.forClass(NotificationCommand.class);
        verify(notificationPublisher, org.mockito.Mockito.atLeastOnce()).enqueue(captor.capture());
        return captor.getValue();
    }

    private EventPayoutAdjustment factor(Users user, String factor, String reason) {
        EventPayoutAdjustment adjustment = adjustment(user, reason);
        adjustment.setFactor(new BigDecimal(factor));
        return adjustment;
    }

    private EventPayoutAdjustment fixed(Users user, String amount, String reason) {
        EventPayoutAdjustment adjustment = adjustment(user, reason);
        adjustment.setFixedAmount(amount(amount));
        return adjustment;
    }

    private EventPayoutAdjustment adjustment(Users user, String reason) {
        EventPayoutAdjustment adjustment = new EventPayoutAdjustment();
        adjustment.setId(100L + adjustments.size());
        adjustment.setEventId(70L);
        adjustment.setUserId(user.getId());
        adjustment.setReason(reason);
        adjustments.add(adjustment);
        return adjustment;
    }

    private FinancialMovement movement(Long id, FinancialDirection direction, String amount) {
        FinancialMovement movement = new FinancialMovement();
        movement.setId(id);
        movement.setEvent(concert);
        movement.setDirection(direction);
        movement.setNature(FinancialMovementNature.ORDINARY);
        movement.setAmount(amount(amount));
        movements.add(movement);
        return movement;
    }

    private EventPayoutSettlement settlement(Long movementId, Users user, String amount) {
        EventPayoutSettlement settlement = new EventPayoutSettlement();
        settlement.setId(200L + settlements.size());
        settlement.setMovementId(movementId);
        settlement.setEventId(70L);
        settlement.setUserId(user.getId());
        settlement.setAmount(amount(amount));
        settlements.add(settlement);
        return settlement;
    }

    private static SettlementRequest settlementRequest(List<SettlementLineRequest> lines) {
        return new SettlementRequest(10L, null, EVENT_DATE.plusDays(10), null, null, null, lines);
    }

    private static MovementDTO movementDto(Long id, String amount) {
        return new MovementDTO(
            id, 2026, 10L, "Cassa", 140L, "Compensi ai presenti", 70L, "Concerto di primavera", FinancialDirection.EXPENSE,
            FinancialMovementNature.ORDINARY, EVENT_DATE.plusDays(10), null, new BigDecimal(amount), "EUR", "Compenso", "Anna Bianchi",
            null, null, null, false, null, null, 0, 0
        );
    }

    private static PayoutSetting setting(Long id, LocalDate validFrom, String percent, String step) {
        PayoutSetting setting = new PayoutSetting();
        setting.setId(id);
        setting.setValidFrom(validFrom);
        setting.setAssociationSharePercent(new BigDecimal(percent));
        setting.setRoundingStep(new BigDecimal(step));
        return setting;
    }

    private static CalendarEvents event(Long id, String name, LocalDate date, String fee, String cost, List<Users> present) {
        CalendarEvents event = new CalendarEvents();
        event.setId(id);
        event.setName(name);
        event.setDeleted(false);
        event.setStartDate(Date.from(date.atTime(21, 0).atZone(ZoneId.systemDefault()).toInstant()));
        if (fee != null) event.setFee(new BigDecimal(fee));
        if (cost != null) {
            EventCost item = new EventCost();
            item.setDescription("Service audio");
            item.setAmount(new BigDecimal(cost));
            event.getCosts().add(item);
        }
        present.forEach(user -> {
            CalendarEventPresence presence = new CalendarEventPresence();
            presence.setUser(user);
            event.getPresences().add(presence);
        });
        return event;
    }

    private static Users user(Long id, String name, String lastName) {
        Users user = new Users();
        user.setId(id);
        user.setName(name);
        user.setLastName(lastName);
        return user;
    }

    private static BigDecimal amount(String value) {
        return new BigDecimal(value).setScale(4);
    }

    private static JwtAuthenticationToken authentication() {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("user-1").claim("tenant", "tenant-a")
            .claim("given_name", "Mario").claim("family_name", "Rossi")
            .issuedAt(now).expiresAt(now.plusSeconds(300)).build();
        return new JwtAuthenticationToken(jwt);
    }
}
