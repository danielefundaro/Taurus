package com.fundaro.zodiac.taurus.service.finance;

import com.fundaro.zodiac.taurus.domain.CalendarEventPresence;
import com.fundaro.zodiac.taurus.domain.CalendarEvents;
import com.fundaro.zodiac.taurus.domain.EventCost;
import com.fundaro.zodiac.taurus.domain.Users;
import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.domain.finance.EventPayoutAdjustment;
import com.fundaro.zodiac.taurus.domain.finance.EventPayoutSettlement;
import com.fundaro.zodiac.taurus.domain.finance.FinancialCategory;
import com.fundaro.zodiac.taurus.domain.finance.FinancialDirection;
import com.fundaro.zodiac.taurus.domain.finance.FinancialMovement;
import com.fundaro.zodiac.taurus.domain.finance.PayoutSetting;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSeverity;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSource;
import com.fundaro.zodiac.taurus.repository.CalendarEventsRepository;
import com.fundaro.zodiac.taurus.repository.UsersRepository;
import com.fundaro.zodiac.taurus.repository.finance.EventPayoutAdjustmentRepository;
import com.fundaro.zodiac.taurus.repository.finance.EventPayoutSettlementRepository;
import com.fundaro.zodiac.taurus.repository.finance.FinancialCategoryRepository;
import com.fundaro.zodiac.taurus.repository.finance.FinancialMovementRepository;
import com.fundaro.zodiac.taurus.repository.finance.PayoutSettingRepository;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.MovementDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.FinanceDtos.MovementRequest;
import com.fundaro.zodiac.taurus.service.dto.finance.PayoutDtos.AdjustmentRequest;
import com.fundaro.zodiac.taurus.service.dto.finance.PayoutDtos.DistributionDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.PayoutDtos.EventLineDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.PayoutDtos.EventPayoutDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.PayoutDtos.MemberLineDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.PayoutDtos.MemberPayoutsDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.PayoutDtos.MemberSummaryDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.PayoutDtos.SettingDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.PayoutDtos.SettingRequest;
import com.fundaro.zodiac.taurus.service.dto.finance.PayoutDtos.SettledEventDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.PayoutDtos.SettlementDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.PayoutDtos.SettlementLineDTO;
import com.fundaro.zodiac.taurus.service.dto.finance.PayoutDtos.SettlementLineRequest;
import com.fundaro.zodiac.taurus.service.dto.finance.PayoutDtos.SettlementRequest;
import com.fundaro.zodiac.taurus.service.finance.PayoutCalculator.Distribution;
import com.fundaro.zodiac.taurus.service.finance.PayoutCalculator.Line;
import com.fundaro.zodiac.taurus.service.finance.PayoutCalculator.Participant;
import com.fundaro.zodiac.taurus.service.impl.FinanceService;
import com.fundaro.zodiac.taurus.service.impl.NotificationOutboxPublisher;
import com.fundaro.zodiac.taurus.service.notification.NotificationAudience;
import com.fundaro.zodiac.taurus.service.notification.NotificationCommand;
import com.fundaro.zodiac.taurus.service.notification.NotificationEventKey;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The shares of the participants of the events (docs/event-payout-shares-spec.md): dated parameters, factors and fixed
 * payments, the simulation on the planned and on the actual figures, the shares due to each member and their settlement
 * with one expense movement. The shares are never stored: they are recalculated from presences, budget, movements,
 * parameters and adjustments, and only what was paid is kept.
 */
@Service
@Transactional
public class PayoutService {

    private static final Logger LOG = LoggerFactory.getLogger(PayoutService.class);

    private static final String ENTITY = "payout";
    static final String AGGREGATE_TYPE = "EVENT_PAYOUT";
    /** The system category of the settlements, created in every tenant schema by the migration. */
    static final String DEFAULT_CATEGORY = "Compensi ai presenti";
    static final String SHARE = "SHARE";
    static final String FIXED = "FIXED";

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(PayoutCalculator.SCALE);
    private static final BigDecimal ONE = BigDecimal.ONE;
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final LocalDate END_OF_TIME = LocalDate.of(9999, 12, 31);
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Set<RoleEnum> NOTIFICATION_ROLES = Set.of(
        RoleEnum.ROLE_ADMIN,
        RoleEnum.ROLE_BOARD,
        RoleEnum.ROLE_SUPER_ADMIN,
        RoleEnum.ROLE_TREASURER
    );

    private final PayoutSettingRepository settingRepository;
    private final EventPayoutAdjustmentRepository adjustmentRepository;
    private final EventPayoutSettlementRepository settlementRepository;
    private final CalendarEventsRepository eventRepository;
    private final UsersRepository userRepository;
    private final FinancialMovementRepository movementRepository;
    private final FinancialCategoryRepository categoryRepository;
    private final FinanceService financeService;
    private NotificationOutboxPublisher notificationPublisher;

    public PayoutService(
        PayoutSettingRepository settingRepository,
        EventPayoutAdjustmentRepository adjustmentRepository,
        EventPayoutSettlementRepository settlementRepository,
        CalendarEventsRepository eventRepository,
        UsersRepository userRepository,
        FinancialMovementRepository movementRepository,
        FinancialCategoryRepository categoryRepository,
        FinanceService financeService
    ) {
        this.settingRepository = settingRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.settlementRepository = settlementRepository;
        this.eventRepository = eventRepository;
        this.userRepository = userRepository;
        this.movementRepository = movementRepository;
        this.categoryRepository = categoryRepository;
        this.financeService = financeService;
    }

    @Autowired(required = false)
    void setNotificationPublisher(NotificationOutboxPublisher notificationPublisher) {
        this.notificationPublisher = notificationPublisher;
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Dated parameters

    @Transactional(readOnly = true)
    public List<SettingDTO> findSettings(AbstractAuthenticationToken token) {
        tenant(token);
        return toSettingDtos(settingRepository.findAllByDeletedFalseOrderByValidFromAsc());
    }

    public SettingDTO createSetting(SettingRequest request, AbstractAuthenticationToken token) {
        tenant(token);
        String actor = actor(token);
        if (settingRepository.existsByValidFromAndDeletedFalse(request.validFrom())) throw dateExists();
        PayoutSetting setting = new PayoutSetting();
        setting.initializeAudit(actor);
        applySetting(setting, request);
        settingRepository.save(setting);
        LOG.info("payout: creata la versione {} dei parametri con decorrenza {}", setting.getId(), setting.getValidFrom());
        notify(
            "PAYOUT_SETTING_CREATED",
            setting.getId(),
            "Economia: parametri dei compensi creati",
            displayName(token) + " ha creato i parametri dei compensi con decorrenza " + date(setting.getValidFrom()) + ": "
                + settingSummary(setting) + ".",
            NotificationSeverity.INFO,
            "/finance?tab=payouts&view=settings",
            token
        );
        return toSettingDtos(List.of(setting)).getFirst();
    }

    public SettingDTO updateSetting(long id, SettingRequest request, AbstractAuthenticationToken token) {
        tenant(token);
        String actor = actor(token);
        PayoutSetting setting = requiredSetting(id);
        if (settingRepository.existsByValidFromAndIdNotAndDeletedFalse(request.validFrom(), id)) throw dateExists();
        // Moving the first version later leaves the events between the two dates without parameters.
        if (request.validFrom().isAfter(setting.getValidFrom())) {
            requireCoverage(setting, request.validFrom());
        }
        applySetting(setting, request);
        setting.touchAudit(actor);
        settingRepository.save(setting);
        LOG.info("payout: aggiornata la versione {} dei parametri con decorrenza {}", id, setting.getValidFrom());
        notify(
            "PAYOUT_SETTING_UPDATED",
            setting.getId(),
            "Economia: parametri dei compensi aggiornati",
            displayName(token) + " ha aggiornato i parametri dei compensi con decorrenza " + date(setting.getValidFrom()) + ": "
                + settingSummary(setting) + ".",
            NotificationSeverity.INFO,
            "/finance?tab=payouts&view=settings",
            token
        );
        return toSettingDtos(List.of(setting)).getFirst();
    }

    /** Allowed only when the events with present users that the version covers fall back on an earlier version. */
    public void deleteSetting(long id, AbstractAuthenticationToken token) {
        tenant(token);
        String actor = actor(token);
        PayoutSetting setting = requiredSetting(id);
        requireCoverage(setting, END_OF_TIME);
        setting.setDeleted(true);
        setting.touchAudit(actor);
        settingRepository.save(setting);
        LOG.info("payout: rimossa la versione {} dei parametri con decorrenza {}", id, setting.getValidFrom());
        notify(
            "PAYOUT_SETTING_REMOVED",
            setting.getId(),
            "Economia: parametri dei compensi rimossi",
            displayName(token) + " ha rimosso i parametri dei compensi con decorrenza " + date(setting.getValidFrom()) + ".",
            NotificationSeverity.WARNING,
            "/finance?tab=payouts&view=settings",
            token
        );
    }

    /**
     * The events already settled that a version with the given date would recalculate: the ones from that date to the
     * next version. The interface lists them before a retroactive version is saved.
     */
    @Transactional(readOnly = true)
    public List<SettledEventDTO> settledEventsAffectedBy(LocalDate validFrom, Long settingId, AbstractAuthenticationToken token) {
        tenant(token);
        LocalDate until = settingRepository.findAllByDeletedFalseOrderByValidFromAsc().stream()
            .filter(setting -> !setting.getId().equals(settingId))
            .map(PayoutSetting::getValidFrom)
            .filter(value -> value.isAfter(validFrom))
            .findFirst()
            .orElse(END_OF_TIME);
        return settlementRepository.findSettledEventsBetween(startOf(validFrom), startOf(until)).stream()
            .map(row -> new SettledEventDTO(row.getEventId(), row.getEventName(), toLocalDate(row.getStartDate()), money(row.getSettledAmount())))
            .toList();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Simulation of an event

    /**
     * The shares of the event on the planned and on the actual figures. The preview values replace the saved parameters
     * for this answer only: nothing is stored.
     */
    @Transactional(readOnly = true)
    public EventPayoutDTO simulate(long eventId, BigDecimal previewPercent, BigDecimal previewStep, AbstractAuthenticationToken token) {
        tenant(token);
        if (previewPercent != null && (previewPercent.signum() < 0 || previewPercent.compareTo(HUNDRED) > 0)
            || previewStep != null && previewStep.signum() < 0) {
            throw error(HttpStatus.BAD_REQUEST, "Parametri di anteprima non validi", "payout.preview.invalid");
        }
        CalendarEvents event = requiredEvent(eventId);
        LocalDate eventDate = eventDate(event);
        PayoutSetting setting = applicable(settingRepository.findAllByDeletedFalseOrderByValidFromAsc(), eventDate).orElseThrow(() -> {
            LOG.info("payout: simulazione rifiutata per l'evento {}, nessun parametro applicabile al {}", eventId, eventDate);
            return error(
                HttpStatus.CONFLICT,
                "Nessuna versione dei parametri dei compensi è applicabile alla data dell'evento",
                "payout.setting.notConfigured"
            );
        });
        BigDecimal percent = Objects.requireNonNullElse(previewPercent, setting.getAssociationSharePercent());
        BigDecimal step = Objects.requireNonNullElse(previewStep, setting.getRoundingStep());
        Context context = context(List.of(event));
        Computation computation = compute(event, setting, percent, step, context);
        boolean preview = percent.compareTo(setting.getAssociationSharePercent()) != 0 || step.compareTo(setting.getRoundingStep()) != 0;
        List<EventLineDTO> lines = computation.lines().stream().map(this::toEventLine).toList();
        return new EventPayoutDTO(
            event.getId(),
            event.getName(),
            eventDate,
            setting.getId(),
            setting.getValidFrom(),
            setting.getAssociationSharePercent(),
            setting.getRoundingStep(),
            percent,
            step,
            preview,
            computation.incomeRecorded(),
            toDistribution(computation.planned()),
            toDistribution(computation.actual()),
            lines,
            computation.lines().stream().map(Share::settled).reduce(ZERO, BigDecimal::add),
            computation.lines().stream().map(Share::due).reduce(ZERO, BigDecimal::add),
            computation.lines().stream().map(Share::deviation).reduce(ZERO, BigDecimal::add)
        );
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Factors and fixed payments

    public void setAdjustment(long eventId, long userId, AdjustmentRequest request, AbstractAuthenticationToken token) {
        tenant(token);
        String actor = actor(token);
        String reason = trimToNull(request.reason());
        if (reason == null) {
            throw error(HttpStatus.BAD_REQUEST, "Il motivo del fattore o del pagamento fisso è obbligatorio", "payout.adjustment.reasonRequired");
        }
        if ((request.factor() == null) == (request.fixedAmount() == null)) {
            throw error(HttpStatus.BAD_REQUEST, "Indicare un fattore oppure un pagamento fisso", "payout.adjustment.valueRequired");
        }
        BigDecimal factor = request.factor() == null ? null : request.factor().setScale(3, RoundingMode.HALF_UP);
        if (factor != null && (factor.signum() < 0 || factor.compareTo(ONE) > 0)) {
            throw error(HttpStatus.BAD_REQUEST, "Il fattore deve essere compreso fra 0 e 1", "payout.adjustment.factorOutOfRange");
        }
        BigDecimal fixedAmount = request.fixedAmount() == null ? null : request.fixedAmount().setScale(2, RoundingMode.HALF_UP);
        if (fixedAmount != null && fixedAmount.signum() <= 0) {
            throw error(HttpStatus.BAD_REQUEST, "Il pagamento fisso deve essere maggiore di zero", "payout.adjustment.fixedAmountNotPositive");
        }
        CalendarEvents event = requiredEvent(eventId);
        Users user = requiredUser(userId);
        if (presentUsers(event).stream().noneMatch(present -> present.getId().equals(userId))) {
            throw error(HttpStatus.CONFLICT, "L'utente non risulta presente all'evento", "payout.adjustment.userNotPresent");
        }
        Optional<EventPayoutAdjustment> existing = adjustmentRepository.findByEventIdAndUserIdAndDeletedFalse(eventId, userId);
        // A factor of 1 is the base share: it is not stored, and it removes what the participant had.
        if (factor != null && factor.compareTo(ONE) == 0) {
            existing.ifPresent(adjustment -> remove(adjustment, event, user, actor, token));
            return;
        }
        EventPayoutAdjustment adjustment = existing.orElseGet(() -> {
            EventPayoutAdjustment created = new EventPayoutAdjustment();
            created.initializeAudit(actor);
            created.setEventId(eventId);
            created.setUserId(userId);
            return created;
        });
        if (adjustment.getId() != null) adjustment.touchAudit(actor);
        adjustment.setFactor(factor);
        adjustment.setFixedAmount(fixedAmount == null ? null : fixedAmount.setScale(PayoutCalculator.SCALE, RoundingMode.UNNECESSARY));
        adjustment.setReason(reason);
        adjustmentRepository.save(adjustment);
        LOG.info(
            "payout: assegnato {} all'utente {} per l'evento {} con motivo \"{}\"",
            factor != null ? "il fattore " + factor.toPlainString() : "un pagamento fisso",
            userId,
            eventId,
            reason
        );
        notify(
            "PAYOUT_ADJUSTMENT_SET",
            eventId,
            factor != null ? "Economia: fattore assegnato" : "Economia: pagamento fisso assegnato",
            displayName(token)
                + (factor != null
                ? " ha assegnato a " + userName(user) + " il fattore " + decimal(factor, 3)
                : " ha assegnato a " + userName(user) + " un pagamento fisso di " + formatMoney(fixedAmount))
                + " per " + eventLabel(event) + ". Motivazione: " + reason + ".",
            NotificationSeverity.INFO,
            eventPath(eventId),
            token
        );
    }

    /** Brings the participant back to the base share, without factor or fixed payment; nothing to do when there is none. */
    public void removeAdjustment(long eventId, long userId, AbstractAuthenticationToken token) {
        tenant(token);
        String actor = actor(token);
        EventPayoutAdjustment existing = adjustmentRepository.findByEventIdAndUserIdAndDeletedFalse(eventId, userId).orElse(null);
        if (existing == null) return;
        CalendarEvents event = requiredEvent(eventId);
        Users user = requiredUser(userId);
        remove(existing, event, user, actor, token);
    }

    private void remove(EventPayoutAdjustment adjustment, CalendarEvents event, Users user, String actor, AbstractAuthenticationToken token) {
        adjustment.setDeleted(true);
        adjustment.touchAudit(actor);
        adjustmentRepository.save(adjustment);
        boolean factor = adjustment.getFactor() != null;
        LOG.info("payout: rimosso {} dell'utente {} per l'evento {}", factor ? "il fattore" : "il pagamento fisso", user.getId(), event.getId());
        notify(
            "PAYOUT_ADJUSTMENT_REMOVED",
            event.getId(),
            factor ? "Economia: fattore rimosso" : "Economia: pagamento fisso rimosso",
            displayName(token)
                + (factor
                ? " ha rimosso il fattore " + decimal(adjustment.getFactor(), 3) + " di "
                : " ha rimosso il pagamento fisso di " + formatMoney(adjustment.getFixedAmount()) + " di ")
                + userName(user) + " per " + eventLabel(event) + ".",
            NotificationSeverity.INFO,
            eventPath(event.getId()),
            token
        );
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Shares of the members

    /** The members with shares or fixed payments in the period, with what is due and what was paid. */
    @Transactional(readOnly = true)
    public List<MemberSummaryDTO> findMembers(LocalDate from, LocalDate to, AbstractAuthenticationToken token) {
        tenant(token);
        List<CalendarEvents> events = inPeriod(eventRepository.findAllWithPresences(), from, to);
        Map<Long, List<Share>> sharesByUser = new LinkedHashMap<>();
        List<PayoutSetting> settings = settingRepository.findAllByDeletedFalseOrderByValidFromAsc();
        Context context = context(events);
        for (CalendarEvents event : events) {
            for (Share share : computeSaved(event, settings, context).lines()) {
                sharesByUser.computeIfAbsent(share.userId(), key -> new ArrayList<>()).add(share);
            }
        }
        Map<Long, Users> users = usersById(sharesByUser.keySet());
        return sharesByUser.entrySet().stream()
            .filter(entry -> users.containsKey(entry.getKey()))
            .map(entry -> new MemberSummaryDTO(
                entry.getKey(),
                userName(users.get(entry.getKey())),
                entry.getValue().size(),
                entry.getValue().stream().map(Share::due).reduce(ZERO, BigDecimal::add),
                entry.getValue().stream().map(Share::settled).reduce(ZERO, BigDecimal::add),
                entry.getValue().stream().map(Share::deviation).reduce(ZERO, BigDecimal::add)
            ))
            .sorted(Comparator.comparing(MemberSummaryDTO::name, String.CASE_INSENSITIVE_ORDER))
            .toList();
    }

    /**
     * The actual shares and the fixed payments of a member, event by event. {@code status} is {@code DUE} for what is
     * still to pay, {@code SETTLED} for what is fully paid, anything else for all.
     */
    @Transactional(readOnly = true)
    public MemberPayoutsDTO findMember(long userId, LocalDate from, LocalDate to, String status, AbstractAuthenticationToken token) {
        tenant(token);
        Users user = requiredUser(userId);
        Map<Long, CalendarEvents> events = new LinkedHashMap<>();
        eventRepository.findAllWithPresentUser(userId).forEach(event -> events.put(event.getId(), event));
        Set<Long> settledEventIds = settlementRepository.findAllByUserIdAndDeletedFalse(userId).stream()
            .map(EventPayoutSettlement::getEventId)
            .filter(eventId -> !events.containsKey(eventId))
            .collect(Collectors.toCollection(LinkedHashSet::new));
        eventRepository.findAllById(settledEventIds).stream()
            .filter(event -> !Boolean.TRUE.equals(event.getDeleted()))
            .forEach(event -> events.put(event.getId(), event));
        List<CalendarEvents> selected = inPeriod(events.values(), from, to);
        List<PayoutSetting> settings = settingRepository.findAllByDeletedFalseOrderByValidFromAsc();
        Context context = context(selected);
        List<MemberLineDTO> lines = new ArrayList<>();
        for (CalendarEvents event : selected) {
            Computation computation = computeSaved(event, settings, context);
            computation.lines().stream()
                .filter(share -> share.userId().equals(userId))
                .filter(share -> matches(share, status))
                .findFirst()
                .ifPresent(share -> lines.add(new MemberLineDTO(
                    event.getId(),
                    event.getName(),
                    eventDate(event),
                    share.present(),
                    computation.configured(),
                    computation.incomeRecorded(),
                    share.kind(),
                    share.factor(),
                    share.fixedAmount(),
                    share.reason(),
                    share.simulatedShare(),
                    share.realShare(),
                    share.settled(),
                    share.due(),
                    share.deviation()
                )));
        }
        lines.sort(Comparator.comparing(MemberLineDTO::eventDate, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(MemberLineDTO::eventId, Comparator.reverseOrder()));
        return new MemberPayoutsDTO(
            userId,
            userName(user),
            lines.stream().map(MemberLineDTO::due).reduce(ZERO, BigDecimal::add),
            lines.stream().map(MemberLineDTO::settled).reduce(ZERO, BigDecimal::add),
            lines.stream().map(MemberLineDTO::deviation).reduce(ZERO, BigDecimal::add),
            lines
        );
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Settlement

    /**
     * One expense movement for one member, and a settlement line for each event it pays, in one transaction. Each line
     * is at most what is still due on the actual share or on the fixed payment; a repeated request key returns the
     * settlement already registered.
     */
    public SettlementDTO settle(SettlementRequest request, AbstractAuthenticationToken token) {
        tenant(token);
        String actor = actor(token);
        List<SettlementLineRequest> requested = request.lines() == null ? List.of() : request.lines();
        if (requested.isEmpty()) {
            throw error(HttpStatus.CONFLICT, "La liquidazione non contiene righe", "payout.settlement.empty");
        }
        Set<Long> userIds = requested.stream().map(SettlementLineRequest::userId).collect(Collectors.toSet());
        if (userIds.size() > 1) {
            throw error(HttpStatus.BAD_REQUEST, "Una liquidazione riguarda un solo socio", "payout.settlement.multipleUsers");
        }
        if (request.requestKey() != null) {
            FinancialMovement replayed = movementRepository.findByRequestKeyAndDeletedFalse(request.requestKey()).orElse(null);
            if (replayed != null) {
                List<EventPayoutSettlement> lines = settlementRepository.findAllByMovementIdAndDeletedFalse(replayed.getId());
                if (!lines.isEmpty()) return toSettlementDto(replayed, lines);
            }
        }
        Long userId = userIds.iterator().next();
        Users user = requiredUser(userId);
        Map<Long, BigDecimal> amounts = new LinkedHashMap<>();
        requested.forEach(line -> amounts.merge(line.eventId(), line.amount().setScale(2, RoundingMode.HALF_UP), BigDecimal::add));

        List<CalendarEvents> events = amounts.keySet().stream().map(this::requiredEvent).toList();
        List<PayoutSetting> settings = settingRepository.findAllByDeletedFalseOrderByValidFromAsc();
        Context context = context(events);
        for (CalendarEvents event : events) {
            BigDecimal due = computeSaved(event, settings, context).lines().stream()
                .filter(share -> share.userId().equals(userId))
                .map(Share::due)
                .findFirst()
                .orElse(ZERO);
            if (amounts.get(event.getId()).compareTo(due) > 0) {
                throw error(
                    HttpStatus.BAD_REQUEST,
                    "L'importo da liquidare supera il residuo dovuto per l'evento",
                    "payout.settlement.amountExceedsDue"
                );
            }
        }

        BigDecimal total = amounts.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        CalendarEvents single = events.size() == 1 ? events.getFirst() : null;
        Long categoryId = request.categoryId() != null
            ? request.categoryId()
            : categoryRepository.findFirstByNameIgnoreCaseAndDeletedFalseAndActiveTrueOrderByIdAsc(DEFAULT_CATEGORY)
                .map(FinancialCategory::getId)
                .orElse(null);
        String description = Objects.requireNonNullElse(
            trimToNull(request.description()),
            single != null ? "Compenso per " + eventLabel(single) : "Compensi per " + events.size() + " eventi"
        );
        MovementDTO movement = financeService.createPayoutMovement(
            new MovementRequest(
                request.accountId(),
                categoryId,
                single == null ? null : single.getId(),
                FinancialDirection.EXPENSE,
                request.bookingDate(),
                null,
                total,
                description,
                userName(user),
                null,
                trimToNull(request.notes()),
                request.requestKey(),
                null
            ),
            actor
        );
        List<EventPayoutSettlement> lines = new ArrayList<>();
        for (CalendarEvents event : events) {
            EventPayoutSettlement settlement = new EventPayoutSettlement();
            settlement.initializeAudit(actor);
            settlement.setMovementId(movement.id());
            settlement.setEventId(event.getId());
            settlement.setUserId(userId);
            settlement.setAmount(amounts.get(event.getId()).setScale(PayoutCalculator.SCALE, RoundingMode.UNNECESSARY));
            settlement.setEventNameSnapshot(event.getName());
            lines.add(settlement);
        }
        settlementRepository.saveAll(lines);
        LOG.info("payout: liquidati all'utente {} {} su {} righe con il movimento {}", userId, total.toPlainString(), lines.size(), movement.id());
        notify(
            "PAYOUT_SETTLED",
            movement.id(),
            "Economia: compensi liquidati",
            displayName(token) + " ha liquidato a " + userName(user) + " " + formatMoney(total) + " per "
                + (single != null ? eventLabel(single) : events.size() + " eventi") + ".",
            NotificationSeverity.INFO,
            "/finance?tab=payouts&user=" + userId,
            token
        );
        return new SettlementDTO(
            movement.id(),
            userId,
            userName(user),
            movement.bookingDate(),
            money(total),
            lines.size(),
            lines.stream().map(PayoutService::toSettlementLine).toList()
        );
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Computation

    /** What the computation needs about a group of events, loaded once for all of them. */
    private record Context(
        Map<Long, List<FinancialMovement>> movements,
        Set<Long> settlingMovementIds,
        Map<Long, List<EventPayoutAdjustment>> adjustments,
        Map<Long, List<EventPayoutSettlement>> settlements,
        Map<Long, Users> users
    ) {
    }

    /** The share of a participant, or the paid share of a user no longer present. */
    private record Share(
        Long userId,
        String name,
        boolean present,
        String kind,
        BigDecimal factor,
        BigDecimal fixedAmount,
        String reason,
        BigDecimal simulatedTheoreticalShare,
        BigDecimal simulatedShare,
        BigDecimal realTheoreticalShare,
        BigDecimal realShare,
        BigDecimal settled,
        BigDecimal due,
        BigDecimal deviation
    ) {
    }

    private record Computation(boolean configured, boolean incomeRecorded, Distribution planned, Distribution actual, List<Share> lines) {
    }

    private Context context(Collection<CalendarEvents> events) {
        List<Long> eventIds = events.stream().map(CalendarEvents::getId).toList();
        if (eventIds.isEmpty()) return new Context(Map.of(), Set.of(), Map.of(), Map.of(), Map.of());
        List<FinancialMovement> movements = movementRepository.findAllByEvent_IdInAndDeletedFalseOrderByBookingDateAscIdAsc(eventIds);
        List<Long> movementIds = movements.stream().map(FinancialMovement::getId).toList();
        Set<Long> settling = movementIds.isEmpty() ? Set.of() : settlementRepository.findSettlingMovementIds(movementIds);
        List<EventPayoutSettlement> settlements = settlementRepository.findAllByEventIdInAndDeletedFalse(eventIds);
        Set<Long> userIds = new LinkedHashSet<>();
        settlements.forEach(settlement -> userIds.add(settlement.getUserId()));
        events.forEach(event -> presentUsers(event).forEach(user -> userIds.add(user.getId())));
        return new Context(
            movements.stream().collect(Collectors.groupingBy(movement -> movement.getEvent().getId())),
            settling,
            adjustmentRepository.findAllByEventIdInAndDeletedFalse(eventIds).stream().collect(Collectors.groupingBy(EventPayoutAdjustment::getEventId)),
            settlements.stream().collect(Collectors.groupingBy(EventPayoutSettlement::getEventId)),
            usersById(userIds)
        );
    }

    /** The shares with the saved parameters of the event, the only ones that can be settled. */
    private Computation computeSaved(CalendarEvents event, List<PayoutSetting> settings, Context context) {
        PayoutSetting setting = applicable(settings, eventDate(event)).orElse(null);
        return setting == null
            ? compute(event, null, null, null, context)
            : compute(event, setting, setting.getAssociationSharePercent(), setting.getRoundingStep(), context);
    }

    /**
     * Without a setting the shares are unknown and zero, while the fixed payments, which do not depend on the
     * parameters, stay due.
     */
    private Computation compute(CalendarEvents event, PayoutSetting setting, BigDecimal percent, BigDecimal step, Context context) {
        List<FinancialMovement> movements = context.movements().getOrDefault(event.getId(), List.of());
        BigDecimal plannedBase = money(event.getFee());
        BigDecimal plannedExpenses = event.getCosts().stream().map(EventCost::getAmount).filter(Objects::nonNull).reduce(ZERO, BigDecimal::add);
        BigDecimal actualBase = movements.stream()
            .filter(movement -> movement.getDirection() == FinancialDirection.INCOME)
            .map(FinancialMovement::getAmount)
            .reduce(ZERO, BigDecimal::add);
        // The settlements are not expenses of the event: otherwise every payment would lower the share it pays.
        BigDecimal actualExpenses = movements.stream()
            .filter(movement -> movement.getDirection() == FinancialDirection.EXPENSE)
            .filter(movement -> !context.settlingMovementIds().contains(movement.getId()))
            .map(FinancialMovement::getAmount)
            .reduce(ZERO, BigDecimal::add);
        boolean incomeRecorded = movements.stream().anyMatch(movement -> movement.getDirection() == FinancialDirection.INCOME);

        List<Users> present = presentUsers(event);
        Map<Long, EventPayoutAdjustment> adjustments = context.adjustments().getOrDefault(event.getId(), List.of()).stream()
            .collect(Collectors.toMap(EventPayoutAdjustment::getUserId, Function.identity(), (first, second) -> first));
        Map<Long, BigDecimal> settled = new LinkedHashMap<>();
        context.settlements().getOrDefault(event.getId(), List.of())
            .forEach(settlement -> settled.merge(settlement.getUserId(), money(settlement.getAmount()), BigDecimal::add));
        List<Participant> participants = present.stream()
            .map(user -> {
                EventPayoutAdjustment adjustment = adjustments.get(user.getId());
                return new Participant(
                    user.getId(),
                    adjustment == null ? null : adjustment.getFactor(),
                    adjustment == null ? null : adjustment.getFixedAmount()
                );
            })
            .toList();

        Distribution planned = setting == null ? null : PayoutCalculator.distribute(plannedBase, plannedExpenses, percent, step, participants);
        Distribution actual = setting == null ? null : PayoutCalculator.distribute(actualBase, actualExpenses, percent, step, participants);
        List<Share> lines = new ArrayList<>();
        for (int index = 0; index < participants.size(); index++) {
            Participant participant = participants.get(index);
            Users user = present.get(index);
            EventPayoutAdjustment adjustment = adjustments.get(user.getId());
            Line plannedLine = planned == null ? null : planned.lines().get(index);
            Line actualLine = actual == null ? null : actual.lines().get(index);
            BigDecimal simulated = plannedLine != null ? plannedLine.amount() : participant.fixed() ? money(participant.fixedAmount()) : ZERO;
            BigDecimal real = actualLine != null ? actualLine.amount() : participant.fixed() ? money(participant.fixedAmount()) : ZERO;
            BigDecimal paid = Objects.requireNonNullElse(settled.remove(user.getId()), ZERO);
            BigDecimal difference = real.subtract(paid);
            lines.add(new Share(
                user.getId(),
                userName(user),
                true,
                participant.fixed() ? FIXED : SHARE,
                participant.factor(),
                participant.fixed() ? money(participant.fixedAmount()) : null,
                adjustment == null ? null : adjustment.getReason(),
                plannedLine == null ? null : plannedLine.theoreticalShare(),
                simulated,
                actualLine == null ? null : actualLine.theoreticalShare(),
                real,
                paid,
                difference.max(ZERO),
                difference.negate().max(ZERO)
            ));
        }
        // Who was paid and is no longer among the present ones keeps a line: what was paid is all deviation.
        settled.forEach((userId, paid) -> {
            Users user = context.users().get(userId);
            lines.add(new Share(
                userId,
                user == null ? "Utente rimosso" : userName(user),
                false,
                SHARE,
                null,
                null,
                null,
                null,
                ZERO,
                null,
                ZERO,
                paid,
                ZERO,
                paid
            ));
        });
        lines.sort(Comparator.comparing(Share::name, String.CASE_INSENSITIVE_ORDER).thenComparing(Share::userId));
        return new Computation(setting != null, incomeRecorded, planned, actual, lines);
    }

    /** The present users of the event, each once, in the order of the presences. */
    private static List<Users> presentUsers(CalendarEvents event) {
        Map<Long, Users> users = new LinkedHashMap<>();
        for (CalendarEventPresence presence : event.getPresences()) {
            Users user = presence.getUser();
            if (user != null && user.getId() != null) users.putIfAbsent(user.getId(), user);
        }
        return List.copyOf(users.values());
    }

    /** The version with the most recent date not after the date of the event. */
    static Optional<PayoutSetting> applicable(List<PayoutSetting> settings, LocalDate eventDate) {
        if (eventDate == null) return Optional.empty();
        return settings.stream()
            .filter(setting -> !setting.getValidFrom().isAfter(eventDate))
            .max(Comparator.comparing(PayoutSetting::getValidFrom));
    }

    /**
     * Refuses to leave events with present users without parameters: the version may stop covering the events from its
     * date to {@code newStart} only when an earlier version takes them over.
     */
    private void requireCoverage(PayoutSetting setting, LocalDate newStart) {
        List<PayoutSetting> settings = settingRepository.findAllByDeletedFalseOrderByValidFromAsc();
        boolean hasEarlier = settings.stream()
            .anyMatch(other -> !other.getId().equals(setting.getId()) && other.getValidFrom().isBefore(setting.getValidFrom()));
        if (hasEarlier) return;
        LocalDate next = settings.stream()
            .filter(other -> !other.getId().equals(setting.getId()))
            .map(PayoutSetting::getValidFrom)
            .filter(value -> value.isAfter(setting.getValidFrom()))
            .findFirst()
            .orElse(END_OF_TIME);
        LocalDate until = newStart.isBefore(next) ? newStart : next;
        if (eventRepository.countWithPresencesBetween(startOf(setting.getValidFrom()), startOf(until)) > 0) {
            throw error(
                HttpStatus.CONFLICT,
                "La versione è l'unica applicabile ad alcuni eventi con presenti: creare prima una versione con decorrenza anteriore",
                "payout.setting.inUse"
            );
        }
    }

    private static boolean matches(Share share, String status) {
        if ("DUE".equalsIgnoreCase(status)) return share.due().signum() > 0;
        if ("SETTLED".equalsIgnoreCase(status)) return share.settled().signum() > 0 && share.due().signum() == 0;
        return true;
    }

    private static List<CalendarEvents> inPeriod(Collection<CalendarEvents> events, LocalDate from, LocalDate to) {
        return events.stream()
            .filter(event -> {
                LocalDate date = eventDate(event);
                if (date == null) return from == null && to == null;
                return (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to));
            })
            .toList();
    }

    // ---------------------------------------------------------------------------------------------------------------
    // Mapping and helpers

    private EventLineDTO toEventLine(Share share) {
        return new EventLineDTO(
            share.userId(),
            share.name(),
            share.present(),
            share.kind(),
            share.factor(),
            share.fixedAmount(),
            share.reason(),
            share.simulatedTheoreticalShare(),
            share.simulatedShare(),
            share.realTheoreticalShare(),
            share.realShare(),
            share.settled(),
            share.due(),
            share.deviation()
        );
    }

    private static DistributionDTO toDistribution(Distribution distribution) {
        return new DistributionDTO(
            distribution.base(),
            distribution.expenses(),
            distribution.fixedPayments(),
            distribution.margin(),
            distribution.fullAssociationShare(),
            distribution.associationShare(),
            distribution.associationShareReduced(),
            distribution.net(),
            distribution.baseShare(),
            distribution.sharesTotal(),
            distribution.residual(),
            distribution.deficit(),
            distribution.effectiveAssociationShare(),
            distribution.sharingParticipants()
        );
    }

    private List<SettingDTO> toSettingDtos(List<PayoutSetting> settings) {
        Map<String, String> authors = authorNames(settings.stream().map(PayoutSetting::getInsertBy).toList());
        return settings.stream().map(setting -> toSettingDto(setting, authors)).toList();
    }

    private static SettingDTO toSettingDto(PayoutSetting setting, Map<String, String> authors) {
        return new SettingDTO(
            setting.getId(),
            setting.getValidFrom(),
            setting.getAssociationSharePercent(),
            setting.getRoundingStep(),
            setting.getInsertBy() == null ? null : authors.get(setting.getInsertBy()),
            setting.getInsertDate()
        );
    }

    /**
     * The names of the members behind the identity ids recorded as authors. An erased member keeps the placeholder name
     * of the erasure; an id without a member of the tenant has no name, rather than showing the identity id.
     */
    private Map<String, String> authorNames(Collection<String> keycloakIds) {
        Set<String> ids = keycloakIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (ids.isEmpty()) return Map.of();
        Map<String, Users> members = new HashMap<>();
        for (Users user : userRepository.findAllByKeycloakIdIn(ids)) {
            // The active member wins over an earlier, deleted row with the same identity.
            members.merge(user.getKeycloakId(), user, (current, other) -> Boolean.TRUE.equals(current.getDeleted()) ? other : current);
        }
        Map<String, String> names = new HashMap<>();
        members.forEach((id, user) -> names.put(id, userName(user)));
        return names;
    }

    private SettlementDTO toSettlementDto(FinancialMovement movement, List<EventPayoutSettlement> lines) {
        Long userId = lines.getFirst().getUserId();
        String name = userRepository.findById(userId).map(PayoutService::userName).orElse(movement.getCounterparty());
        return new SettlementDTO(
            movement.getId(),
            userId,
            name,
            movement.getBookingDate(),
            money(movement.getAmount()),
            lines.size(),
            lines.stream().map(PayoutService::toSettlementLine).toList()
        );
    }

    private static SettlementLineDTO toSettlementLine(EventPayoutSettlement settlement) {
        return new SettlementLineDTO(
            settlement.getId(),
            settlement.getEventId(),
            settlement.getEventNameSnapshot(),
            settlement.getUserId(),
            settlement.getAmount()
        );
    }

    private static void applySetting(PayoutSetting setting, SettingRequest request) {
        setting.setValidFrom(request.validFrom());
        setting.setAssociationSharePercent(request.associationSharePercent().setScale(2, RoundingMode.HALF_UP));
        setting.setRoundingStep(request.roundingStep().setScale(PayoutCalculator.SCALE, RoundingMode.HALF_UP));
    }

    private PayoutSetting requiredSetting(long id) {
        return settingRepository.findByIdAndDeletedFalse(id)
            .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Versione dei parametri non trovata", "payout.setting.notFound"));
    }

    private CalendarEvents requiredEvent(long id) {
        return eventRepository.findByIdAndDeletedFalse(id)
            .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Evento non trovato", "finance.event.notFound"));
    }

    private Users requiredUser(long id) {
        return userRepository.findByIdAndDeletedFalse(id)
            .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Utente non trovato", "payout.user.notFound"));
    }

    private Map<Long, Users> usersById(Collection<Long> ids) {
        if (ids.isEmpty()) return Map.of();
        Map<Long, Users> users = new HashMap<>();
        userRepository.findAllById(ids).forEach(user -> users.put(user.getId(), user));
        return users;
    }

    private static RequestAlertException dateExists() {
        return error(HttpStatus.CONFLICT, "Esiste già una versione dei parametri con questa decorrenza", "payout.setting.dateExists");
    }

    private void notify(
        String operation,
        Long aggregateId,
        String title,
        String message,
        NotificationSeverity severity,
        String targetPath,
        AbstractAuthenticationToken token
    ) {
        if (notificationPublisher == null) return;
        notificationPublisher.enqueue(new NotificationCommand(
            NotificationEventKey.random("finance", "event_payout", operation.toLowerCase(Locale.ROOT)),
            NotificationSource.FINANCE,
            AGGREGATE_TYPE,
            aggregateId == null ? null : aggregateId.toString(),
            operation,
            title,
            message,
            severity,
            targetPath,
            actor(token),
            displayName(token),
            NOTIFICATION_ROLES.stream().map(NotificationAudience::role).collect(Collectors.toSet()),
            null
        ));
    }

    private static String settingSummary(PayoutSetting setting) {
        return "quota di cassa " + decimal(setting.getAssociationSharePercent(), 2) + "%, passo "
            + (setting.getRoundingStep().signum() == 0 ? "nessuno" : formatMoney(setting.getRoundingStep()));
    }

    private static String eventLabel(CalendarEvents event) {
        LocalDate date = eventDate(event);
        return "l'evento “" + Objects.requireNonNullElse(trimToNull(event.getName()), "senza nome") + "”"
            + (date == null ? "" : " del " + date(date));
    }

    private static String eventPath(Long eventId) {
        return "/finance?tab=payouts&event=" + eventId;
    }

    static String userName(Users user) {
        String name = String.join(
            " ",
            Objects.requireNonNullElse(trimToNull(user.getName()), ""),
            Objects.requireNonNullElse(trimToNull(user.getLastName()), "")
        ).trim();
        return name.isEmpty() ? "Utente " + user.getId() : name;
    }

    private static String displayName(AbstractAuthenticationToken token) {
        String firstName = trimToNull(SecurityUtils.getFirstNameFromAuthentication(token));
        String lastName = trimToNull(SecurityUtils.getLastNameFromAuthentication(token));
        String name = String.join(" ", Objects.requireNonNullElse(firstName, ""), Objects.requireNonNullElse(lastName, "")).trim();
        if (!name.isEmpty()) return name;
        if (token instanceof JwtAuthenticationToken jwtToken
            && jwtToken.getTokenAttributes().get("preferred_username") instanceof String preferred
            && trimToNull(preferred) != null) {
            return preferred.trim();
        }
        return "Un utente";
    }

    private static String formatMoney(BigDecimal amount) {
        NumberFormat formatter = NumberFormat.getCurrencyInstance(Locale.ITALY);
        return formatter.format(Objects.requireNonNullElse(amount, BigDecimal.ZERO));
    }

    private static String decimal(BigDecimal value, int maximumFractionDigits) {
        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.ITALY);
        formatter.setMinimumFractionDigits(0);
        formatter.setMaximumFractionDigits(maximumFractionDigits);
        return formatter.format(value);
    }

    private static String date(LocalDate value) {
        return value.format(DATE);
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? ZERO : value.setScale(PayoutCalculator.SCALE, RoundingMode.HALF_UP);
    }

    static LocalDate eventDate(CalendarEvents event) {
        return event.getStartDate() == null ? null : toLocalDate(event.getStartDate());
    }

    private static LocalDate toLocalDate(Date value) {
        return value == null ? null : Instant.ofEpochMilli(value.getTime()).atZone(ZoneId.systemDefault()).toLocalDate();
    }

    private static Date startOf(LocalDate value) {
        return Date.from(value.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    private static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String actor(AbstractAuthenticationToken token) {
        String value = SecurityUtils.getUserIdFromAuthentication(token);
        if (value == null || value.isBlank()) {
            throw error(HttpStatus.UNAUTHORIZED, "Identità utente non disponibile", "auth.identity.missing");
        }
        return value;
    }

    private static String tenant(AbstractAuthenticationToken token) {
        String value = SecurityUtils.getTenantIdFromAuthentication(token);
        if (value == null || value.isBlank()) throw error(HttpStatus.FORBIDDEN, "Tenant non disponibile", "auth.tenant.missing");
        return value;
    }

    private static RequestAlertException error(HttpStatus status, String message, String key) {
        return new RequestAlertException(status, message, ENTITY, key);
    }
}
