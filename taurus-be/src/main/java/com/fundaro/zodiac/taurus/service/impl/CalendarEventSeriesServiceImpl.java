package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.CalendarEventSeries;
import com.fundaro.zodiac.taurus.domain.CalendarEventSeriesCost;
import com.fundaro.zodiac.taurus.domain.CalendarEventAvailability;
import com.fundaro.zodiac.taurus.domain.CalendarEvents;
import com.fundaro.zodiac.taurus.domain.CommonFieldsOpenSearch;
import com.fundaro.zodiac.taurus.domain.EventCost;
import com.fundaro.zodiac.taurus.domain.enumeration.RecurrenceEndType;
import com.fundaro.zodiac.taurus.domain.enumeration.RecurrenceWeekDay;
import com.fundaro.zodiac.taurus.domain.enumeration.StateEnum;
import com.fundaro.zodiac.taurus.domain.enumeration.TenantFeature;
import com.fundaro.zodiac.taurus.rabbitmq.EventReminderProducer;
import com.fundaro.zodiac.taurus.repository.CalendarEventSeriesRepository;
import com.fundaro.zodiac.taurus.repository.CalendarEventsRepository;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.service.CalendarEventSeriesService;
import com.fundaro.zodiac.taurus.service.RecurringEventGenerator;
import com.fundaro.zodiac.taurus.service.TenantTimeZoneService;
import com.fundaro.zodiac.taurus.service.TenantFeatureService;
import com.fundaro.zodiac.taurus.service.dto.CalendarEventSeriesDTO;
import com.fundaro.zodiac.taurus.service.dto.CalendarEventSeriesPreviewDTO;
import com.fundaro.zodiac.taurus.service.dto.CalendarEventSeriesRequest;
import com.fundaro.zodiac.taurus.service.dto.CalendarEventsDTO;
import com.fundaro.zodiac.taurus.service.dto.EventCostDTO;
import com.fundaro.zodiac.taurus.service.dto.RecurrenceEndDTO;
import com.fundaro.zodiac.taurus.service.dto.RecurrenceRuleDTO;
import com.fundaro.zodiac.taurus.service.mapper.CalendarEventsMapper;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fundaro.zodiac.taurus.service.OccurrenceLimitExceededException;
import com.fundaro.zodiac.taurus.service.calendar.CalendarSeriesMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import com.fundaro.zodiac.taurus.service.calendarfeed.CalendarFeedEventLifecycle;

@Service
@Transactional
public class CalendarEventSeriesServiceImpl implements CalendarEventSeriesService {
    private static final Logger log = LoggerFactory.getLogger(CalendarEventSeriesServiceImpl.class);

    private CalendarFeedEventLifecycle calendarFeedLifecycle;
    private CalendarSeriesMetrics metrics;

    private final CalendarEventSeriesRepository seriesRepository;
    private final CalendarEventsRepository eventRepository;
    private final CalendarEventsMapper eventMapper;
    private final RecurringEventGenerator generator;
    private final TenantTimeZoneService tenantTimeZoneService;
    private final EventReminderProducer reminderProducer;
    private final TenantFeatureService tenantFeatureService;
    private final int maximumOccurrences;

    public CalendarEventSeriesServiceImpl(
        CalendarEventSeriesRepository seriesRepository,
        CalendarEventsRepository eventRepository,
        CalendarEventsMapper eventMapper,
        RecurringEventGenerator generator,
        TenantTimeZoneService tenantTimeZoneService,
        EventReminderProducer reminderProducer,
        TenantFeatureService tenantFeatureService,
        @Value("${application.calendar.recurrence.max-occurrences:500}") int maximumOccurrences
    ) {
        this.seriesRepository = seriesRepository;
        this.eventRepository = eventRepository;
        this.eventMapper = eventMapper;
        this.generator = generator;
        this.tenantTimeZoneService = tenantTimeZoneService;
        this.reminderProducer = reminderProducer;
        this.tenantFeatureService = tenantFeatureService;
        this.maximumOccurrences = maximumOccurrences;
    }

    @Autowired
    void setCalendarFeedLifecycle(CalendarFeedEventLifecycle value) {
        this.calendarFeedLifecycle = value;
    }

    @Autowired
    void setMetrics(CalendarSeriesMetrics value) {
        this.metrics = value;
    }

    @Override
    @Transactional(readOnly = true)
    public CalendarEventSeriesPreviewDTO preview(CalendarEventSeriesRequest request) {
        ZoneId zone = tenantTimeZoneService.currentZoneId();
        SeriesDefinition definition = definition(request, zone);
        CalendarEventSeriesPreviewDTO result = new CalendarEventSeriesPreviewDTO();
        result.setTimeZone(zone.getId());
        result.setOccurrenceCount(definition.occurrences().size());
        result.setOccurrences(definition.occurrences().stream().limit(20).map(value -> Date.from(value.toInstant())).toList());
        result.setLastOccurrence(Date.from(definition.occurrences().get(definition.occurrences().size() - 1).toInstant()));
        result.setSkippedMonths(definition.skippedMonths().stream().map(YearMonth::toString).toList());
        return result;
    }

    @Override
    public CalendarEventSeriesDTO create(CalendarEventSeriesRequest request, AbstractAuthenticationToken token) {
        long startedAt = System.nanoTime();
        String idempotencyKey = idempotencyKey(request);
        if (idempotencyKey != null) {
            // Richiesta ripetuta dopo un timeout: la serie esiste già e viene restituita come è.
            CalendarEventSeriesDTO replay = seriesRepository
                .findByIdempotencyKey(idempotencyKey)
                .map(existing -> {
                    CalendarEventSeriesDTO dto = toDto(existing);
                    dto.setReplayed(true);
                    dto.setCreatedCount(dto.getOccurrenceCount());
                    log.info("calendar_series_create_replayed seriesId={} occurrences={}", existing.getId(), dto.getOccurrenceCount());
                    return dto;
                })
                .orElse(null);
            if (replay != null) return replay;
        }
        ZoneId zone = tenantTimeZoneService.currentZoneId();
        SeriesDefinition definition = definition(request, zone);
        String actor = actor(token);
        boolean economicsWritable = financeEnabled();

        CalendarEventSeries series = new CalendarEventSeries();
        initialize(series, actor);
        applySeriesDefinition(series, request, definition, actor, economicsWritable);
        series.setIdempotencyKey(idempotencyKey);
        series = saveSeries(series, idempotencyKey);

        List<CalendarEvents> events = new ArrayList<>();
        for (int index = 0; index < definition.occurrences().size(); index++) {
            events.add(createOccurrence(series, request.getTemplate(), definition.occurrences().get(index), index + 1, definition.durationMinutes(), actor, economicsWritable));
        }
        eventRepository.saveAll(events);
        eventRepository.flush();

        CalendarEventSeriesDTO result = toDto(series);
        result.setCreatedCount(events.size());
        if (metrics != null) {
            metrics.recordSeries(CalendarSeriesMetrics.SeriesOperation.CREATED);
            metrics.recordOccurrences(CalendarSeriesMetrics.OccurrenceOutcome.CREATED, events.size());
            metrics.recordBulk(CalendarSeriesMetrics.SeriesOperation.CREATED, events.size(), System.nanoTime() - startedAt);
        }
        // Nessuna descrizione nel log: solo identificativi e conteggi.
        log.info("calendar_series_created seriesId={} occurrences={}", series.getId(), events.size());
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public CalendarEventSeriesDTO findOne(Long id) {
        return toDto(findSeries(id));
    }

    @Override
    public CalendarEventSeriesDTO update(Long id, CalendarEventSeriesRequest request, AbstractAuthenticationToken token) {
        long startedAt = System.nanoTime();
        CalendarEventSeries series = findSeries(id);
        if (request.getEntityVersion() != null && !Objects.equals(request.getEntityVersion(), series.getEntityVersion())) {
            if (metrics != null) metrics.recordFailure(CalendarSeriesMetrics.FailureReason.VERSION_CONFLICT);
            throw error(HttpStatus.CONFLICT, "The recurring series was modified by another request", "version.conflict");
        }
        ZoneId zone = ZoneId.of(series.getTimeZone());
        SeriesDefinition definition = definition(request, zone);
        String actor = actor(token);
        boolean economicsWritable = financeEnabled();
        Instant now = Instant.now();
        List<CalendarEvents> allEvents = eventRepository.findAllBySeries_IdOrderByOriginalStartDateAsc(id);
        Map<CalendarEvents, CalendarFeedEventLifecycle.Snapshot> feedSnapshots = new IdentityHashMap<>();
        if (calendarFeedLifecycle != null)
            allEvents.forEach(event -> feedSnapshots.put(event, calendarFeedLifecycle.snapshot(event)));
        CalendarEvents sourceOccurrence = findSourceOccurrence(request.getSourceOccurrenceId(), allEvents);
        Integer sourceSequence = sourceOccurrence == null ? null : sourceOccurrence.getSeriesSequence();
        Map<Instant, CalendarEvents> byOriginalStart = new HashMap<>();
        allEvents.forEach(event -> byOriginalStart.put(event.getOriginalStartDate().toInstant(), event));
        Map<Integer, CalendarEvents> bySequence = new HashMap<>();
        allEvents.forEach(event ->
            bySequence.merge(event.getSeriesSequence(), event, (first, second) -> first.getDeleted() ? second : first)
        );
        Map<Instant, Integer> desired = new HashMap<>();
        for (int index = 0; index < definition.occurrences().size(); index++) {
            desired.put(definition.occurrences().get(index).toInstant(), index + 1);
        }

        int deleted = 0;
        for (CalendarEvents event : allEvents) {
            boolean inPropagationScope = sourceSequence == null
                ? event.getStartDate() != null && !event.getStartDate().toInstant().isBefore(now)
                : event.getSeriesSequence() >= sourceSequence;
            boolean stillGenerated = sourceSequence == null
                ? desired.containsKey(event.getOriginalStartDate().toInstant())
                : event.getSeriesSequence() <= definition.occurrences().size();
            if (
                inPropagationScope &&
                    !event.getDeleted() &&
                    !Boolean.TRUE.equals(event.getSeriesException()) &&
                    !stillGenerated
            ) {
                event.setDeleted(true);
                event.setSeriesExcluded(false);
                touch(event, actor);
                reminderProducer.cancelAllPending(event.getId());
                deleted++;
            }
        }
        if (calendarFeedLifecycle != null) {
            allEvents.forEach(event -> calendarFeedLifecycle.apply(event, feedSnapshots.get(event)));
            allEvents.forEach(event -> feedSnapshots.put(event, calendarFeedLifecycle.snapshot(event)));
        }
        eventRepository.saveAll(allEvents);
        eventRepository.flush();
        if (sourceSequence != null) realignOriginalStarts(bySequence, allEvents, sourceSequence, definition.occurrences());

        int created = 0;
        int updated = 0;
        int keptException = 0;
        int skippedPast = 0;
        List<CalendarEvents> changed = new ArrayList<>();
        for (int index = 0; index < definition.occurrences().size(); index++) {
            ZonedDateTime occurrence = definition.occurrences().get(index);
            int sequence = index + 1;
            if (sourceSequence == null && occurrence.toInstant().isBefore(now)) {
                // Lo storico non si riscrive: la posizione passata resta come è.
                skippedPast++;
                continue;
            }
            if (sourceSequence != null && sequence < sourceSequence) continue;
            CalendarEvents existing = sourceSequence == null
                ? byOriginalStart.get(occurrence.toInstant())
                : bySequence.get(sequence);
            if (existing == null) {
                changed.add(createOccurrence(series, request.getTemplate(), occurrence, sequence, definition.durationMinutes(), actor, economicsWritable));
                created++;
            } else if (existing.getDeleted()) {
                if (!Boolean.TRUE.equals(existing.getSeriesExcluded())) {
                    existing.setDeleted(false);
                    existing.setSeriesException(false);
                    applyOccurrenceTemplate(existing, request.getTemplate(), occurrence, definition.durationMinutes(), actor, economicsWritable);
                    existing.setSeriesSequence(sequence);
                    changed.add(existing);
                    updated++;
                }
            } else if (
                !Boolean.TRUE.equals(existing.getSeriesException()) || Objects.equals(existing.getId(), request.getSourceOccurrenceId())
            ) {
                applyOccurrenceTemplate(existing, request.getTemplate(), occurrence, definition.durationMinutes(), actor, economicsWritable);
                existing.setSeriesSequence(sequence);
                if (Objects.equals(existing.getId(), request.getSourceOccurrenceId())) {
                    existing.setSeriesException(false);
                    existing.setSeriesExcluded(false);
                }
                changed.add(existing);
                rescheduleReminders(existing, token);
                updated++;
            } else {
                // Occorrenza marcata eccezione: la modifica del template non la tocca.
                keptException++;
            }
        }

        applySeriesDefinition(series, request, definition, actor, economicsWritable);
        seriesRepository.save(series);
        if (calendarFeedLifecycle != null)
            changed.forEach(event -> calendarFeedLifecycle.apply(event, feedSnapshots.get(event)));
        saveOccurrences(changed);

        CalendarEventSeriesDTO result = toDto(series);
        result.setCreatedCount(created);
        result.setUpdatedCount(updated);
        result.setDeletedCount(deleted);
        result.setKeptExceptionCount(keptException);
        result.setSkippedPastCount(skippedPast);
        int outsideRule = outsideRuleExceptions(allEvents, desired, definition, sourceSequence);
        result.setOutsideRuleExceptionCount(outsideRule);
        if (metrics != null) {
            metrics.recordSeries(CalendarSeriesMetrics.SeriesOperation.UPDATED);
            metrics.recordOccurrences(CalendarSeriesMetrics.OccurrenceOutcome.CREATED, created);
            metrics.recordOccurrences(CalendarSeriesMetrics.OccurrenceOutcome.UPDATED, updated);
            metrics.recordOccurrences(CalendarSeriesMetrics.OccurrenceOutcome.DELETED, deleted);
            metrics.recordOccurrences(CalendarSeriesMetrics.OccurrenceOutcome.KEPT_EXCEPTION, keptException);
            metrics.recordOccurrences(CalendarSeriesMetrics.OccurrenceOutcome.SKIPPED_PAST, skippedPast);
            metrics.recordOccurrences(CalendarSeriesMetrics.OccurrenceOutcome.OUTSIDE_RULE_EXCEPTION, outsideRule);
            metrics.recordReminders(updated, deleted);
            metrics.recordBulk(CalendarSeriesMetrics.SeriesOperation.UPDATED, created + updated + deleted, System.nanoTime() - startedAt);
        }
        log.info(
            "calendar_series_updated seriesId={} scope={} created={} updated={} deleted={} keptExceptions={} skippedPast={} outsideRule={}",
            id, sourceSequence == null ? "future" : "fromOccurrence", created, updated, deleted, keptException, skippedPast, outsideRule
        );
        return result;
    }

    /**
     * Eccezioni che la nuova regola non genera più: restano visibili come occorrenze eccezionali
     * della serie fino a una cancellazione esplicita, quindi chi modifica la serie deve saperlo.
     */
    private int outsideRuleExceptions(
        List<CalendarEvents> allEvents,
        Map<Instant, Integer> desired,
        SeriesDefinition definition,
        Integer sourceSequence
    ) {
        return (int) allEvents.stream()
            .filter(event -> !event.getDeleted() && Boolean.TRUE.equals(event.getSeriesException()))
            .filter(event -> sourceSequence == null
                ? !desired.containsKey(event.getOriginalStartDate().toInstant())
                : event.getSeriesSequence() > definition.occurrences().size())
            .count();
    }

    @Override
    public CalendarEventSeriesDTO deleteFuture(Long id, AbstractAuthenticationToken token) {
        CalendarEventSeries series = findSeries(id);
        String actor = actor(token);
        Instant now = Instant.now();
        int deleted = 0;
        List<CalendarEvents> events = eventRepository.findAllBySeries_IdOrderByOriginalStartDateAsc(id);
        for (CalendarEvents event : events) {
            if (!event.getDeleted() && event.getStartDate() != null && !event.getStartDate().toInstant().isBefore(now)) {
                CalendarFeedEventLifecycle.Snapshot feedBefore = calendarFeedLifecycle == null ? null : calendarFeedLifecycle.snapshot(event);
                event.setDeleted(true);
                event.setSeriesExcluded(true);
                touch(event, actor);
                reminderProducer.cancelAllPending(event.getId());
                if (calendarFeedLifecycle != null) calendarFeedLifecycle.apply(event, feedBefore);
                deleted++;
            }
        }
        series.setDeleted(true);
        touch(series, actor);
        eventRepository.saveAll(events);
        seriesRepository.save(series);
        CalendarEventSeriesDTO result = toDtoIncludingDeleted(series, events);
        result.setDeletedCount(deleted);
        if (metrics != null) {
            metrics.recordSeries(CalendarSeriesMetrics.SeriesOperation.DELETED);
            metrics.recordOccurrences(CalendarSeriesMetrics.OccurrenceOutcome.DELETED, deleted);
            metrics.recordReminders(0, deleted);
        }
        log.info("calendar_series_deleted seriesId={} deletedOccurrences={}", id, deleted);
        return result;
    }

    @Override
    public CalendarEventSeriesDTO restoreOccurrence(Long seriesId, Long eventId, AbstractAuthenticationToken token) {
        CalendarEventSeries series = findSeries(seriesId);
        CalendarEvents event = eventRepository.findById(eventId)
            .filter(value -> value.getSeries() != null && Objects.equals(value.getSeries().getId(), seriesId))
            .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Occurrence not found", "calendar.series.occurrence.notFound"));
        CalendarFeedEventLifecycle.Snapshot feedBefore = calendarFeedLifecycle == null ? null : calendarFeedLifecycle.snapshot(event);
        String actor = actor(token);
        ZonedDateTime original = event.getOriginalStartDate().toInstant().atZone(ZoneId.of(series.getTimeZone()));
        CalendarEventsDTO template = templateFrom(series);
        event.setDeleted(false);
        event.setSeriesException(false);
        event.setSeriesExcluded(false);
        applyOccurrenceTemplate(event, template, original, series.getDurationMinutes(), actor, financeEnabled());
        if (calendarFeedLifecycle != null) calendarFeedLifecycle.apply(event, feedBefore);
        eventRepository.save(event);
        eventRepository.flush();
        rescheduleReminders(event, token);
        return toDto(series);
    }

    private SeriesDefinition definition(CalendarEventSeriesRequest request, ZoneId zone) {
        if (request == null || request.getTemplate() == null || request.getRecurrence() == null) {
            throw error(HttpStatus.BAD_REQUEST, "Template and recurrence are required", "calendar.series.request.invalid");
        }
        CalendarEventsDTO template = request.getTemplate();
        if (template.getName() == null || template.getName().isBlank() || template.getStartDate() == null) {
            throw error(HttpStatus.BAD_REQUEST, "Name and start date are required", "calendar.series.template.invalid");
        }
        if (template.getReminderMinutes() != null && (template.getReminderMinutes() < 0 || template.getReminderMinutes() > 1440)) {
            throw error(HttpStatus.BAD_REQUEST, "Reminder minutes must be between 0 and 1440", "calendar.series.reminder.invalid").with("maximum", 1440);
        }
        Instant start = template.getStartDate().toInstant();
        Instant end = template.getEndDate() == null ? start.plusSeconds(3600) : template.getEndDate().toInstant();
        long duration = Duration.between(start, end).toMinutes();
        if (duration < 1 || duration > Integer.MAX_VALUE) {
            throw error(HttpStatus.BAD_REQUEST, "End date must be after start date", "calendar.series.duration.invalid");
        }
        LocalDateTime firstLocal = start.atZone(zone).toLocalDateTime();
        try {
            RecurringEventGenerator.Generation generation = generator.generateDetailed(firstLocal, zone, request.getRecurrence(), maximumOccurrences);
            if (generation.occurrences().isEmpty())
                throw new IllegalArgumentException("The recurrence does not generate occurrences");
            return new SeriesDefinition(firstLocal, (int) duration, generation.occurrences(), generation.skippedMonths());
        } catch (OccurrenceLimitExceededException exception) {
            // Messaggio dedicato: la regola è valida ma genera più occorrenze del limite della serie.
            if (metrics != null) metrics.recordFailure(CalendarSeriesMetrics.FailureReason.LIMIT_EXCEEDED);
            throw error(HttpStatus.BAD_REQUEST, exception.getMessage(), "calendar.series.recurrence.tooManyOccurrences")
                .with("maximum", exception.maximum());
        } catch (IllegalArgumentException exception) {
            if (metrics != null) metrics.recordFailure(CalendarSeriesMetrics.FailureReason.INVALID_RULE);
            throw error(HttpStatus.BAD_REQUEST, exception.getMessage(), "calendar.series.recurrence.invalid");
        }
    }

    private void applySeriesDefinition(CalendarEventSeries series, CalendarEventSeriesRequest request, SeriesDefinition definition, String actor, boolean economicsWritable) {
        CalendarEventsDTO template = request.getTemplate();
        RecurrenceRuleDTO rule = request.getRecurrence();
        series.setName(template.getName().trim());
        series.setDescription(template.getDescription());
        series.setState(template.getState() == null ? StateEnum.DRAFT : template.getState());
        series.setLocation(template.getLocation());
        if (economicsWritable) series.setFee(template.getFee());
        series.setReminderMinutes(template.getReminderMinutes());
        if (series.getTimeZone() == null) series.setTimeZone(definition.occurrences().get(0).getZone().getId());
        series.setFirstStartLocal(definition.firstStartLocal());
        series.setDurationMinutes(definition.durationMinutes());
        series.setFrequency(rule.getFrequency());
        series.setIntervalValue(rule.getInterval());
        series.setWeekDays(serializeWeekDays(rule));
        series.setEndType(rule.getEnd().getType());
        series.setOccurrenceCount(rule.getEnd().getType() == RecurrenceEndType.COUNT ? rule.getEnd().getCount() : null);
        series.setUntilLocalDate(rule.getEnd().getType() == RecurrenceEndType.UNTIL ? rule.getEnd().getUntil() : null);
        if (economicsWritable) {
            series.getCosts().clear();
            if (template.getCosts() != null) {
                template.getCosts().forEach(cost -> series.getCosts().add(seriesCost(cost, actor)));
            }
        }
        touch(series, actor);
    }

    private CalendarEvents createOccurrence(
        CalendarEventSeries series,
        CalendarEventsDTO template,
        ZonedDateTime occurrence,
        int sequence,
        int durationMinutes,
        String actor,
        boolean economicsWritable
    ) {
        CalendarEvents event = new CalendarEvents();
        initialize(event, actor);
        event.setSeries(series);
        event.setOriginalStartDate(Date.from(occurrence.toInstant()));
        event.setSeriesSequence(sequence);
        event.setSeriesException(false);
        event.setSeriesExcluded(false);
        event.setAvailabilities(new ArrayList<>());
        event.setPresences(new ArrayList<>());
        applyOccurrenceTemplate(event, template, occurrence, durationMinutes, actor, economicsWritable);
        if (calendarFeedLifecycle != null) calendarFeedLifecycle.apply(event, null);
        return event;
    }

    private void applyOccurrenceTemplate(CalendarEvents event, CalendarEventsDTO template, ZonedDateTime occurrence, int durationMinutes, String actor, boolean economicsWritable) {
        event.setName(template.getName().trim());
        event.setDescription(template.getDescription());
        event.setState(template.getState() == null ? StateEnum.DRAFT : template.getState());
        event.setLocation(template.getLocation());
        if (economicsWritable) event.setFee(template.getFee());
        event.setReminderMinutes(template.getReminderMinutes());
        event.setStartDate(Date.from(occurrence.toInstant()));
        event.setEndDate(Date.from(occurrence.plusMinutes(durationMinutes).toInstant()));
        if (economicsWritable) {
            event.getCosts().clear();
            if (template.getCosts() != null) {
                template.getCosts().forEach(cost -> event.getCosts().add(eventCost(cost, actor)));
            }
        }
        touch(event, actor);
    }

    private CalendarEventSeriesDTO toDto(CalendarEventSeries series) {
        return toDtoIncludingDeleted(series, eventRepository.findAllBySeries_IdOrderByOriginalStartDateAsc(series.getId()));
    }

    private CalendarEventSeriesDTO toDtoIncludingDeleted(CalendarEventSeries series, List<CalendarEvents> events) {
        CalendarEventSeriesDTO dto = new CalendarEventSeriesDTO();
        dto.setId(series.getId());
        dto.setEntityVersion(series.getEntityVersion());
        dto.setTimeZone(series.getTimeZone());
        dto.setTemplate(templateFrom(series));
        dto.setRecurrence(ruleFrom(series));
        dto.setOccurrenceCount((int) events.stream().filter(event -> !event.getDeleted()).count());
        dto.setExceptionCount((int) events.stream().filter(event -> !event.getDeleted() && Boolean.TRUE.equals(event.getSeriesException())).count());
        return dto;
    }

    private CalendarEventsDTO templateFrom(CalendarEventSeries series) {
        CalendarEventsDTO template = new CalendarEventsDTO();
        template.setName(series.getName());
        template.setDescription(series.getDescription());
        template.setState(series.getState());
        template.setLocation(series.getLocation());
        template.setFee(series.getFee());
        template.setReminderMinutes(series.getReminderMinutes());
        ZonedDateTime start = series.getFirstStartLocal().atZone(ZoneId.of(series.getTimeZone()));
        template.setStartDate(Date.from(start.toInstant()));
        template.setEndDate(Date.from(start.plusMinutes(series.getDurationMinutes()).toInstant()));
        template.setCosts(series.getCosts().stream().map(cost -> {
            EventCostDTO dto = new EventCostDTO();
            dto.setDescription(cost.getDescription());
            dto.setAmount(cost.getAmount());
            return dto;
        }).toList());
        if (!financeEnabled()) {
            template.setFee(null);
            template.setCosts(List.of());
        }
        return template;
    }

    private RecurrenceRuleDTO ruleFrom(CalendarEventSeries series) {
        RecurrenceRuleDTO rule = new RecurrenceRuleDTO();
        rule.setFrequency(series.getFrequency());
        rule.setInterval(series.getIntervalValue());
        if (series.getWeekDays() != null && !series.getWeekDays().isBlank()) {
            rule.setWeekDays(Arrays.stream(series.getWeekDays().split(",")).map(RecurrenceWeekDay::valueOf).toList());
        }
        RecurrenceEndDTO end = new RecurrenceEndDTO();
        end.setType(series.getEndType());
        end.setCount(series.getOccurrenceCount());
        end.setUntil(series.getUntilLocalDate());
        rule.setEnd(end);
        return rule;
    }

    private String serializeWeekDays(RecurrenceRuleDTO rule) {
        if (rule.getWeekDays() == null || rule.getWeekDays().isEmpty()) return null;
        return rule.getWeekDays().stream()
            .distinct()
            .sorted(Comparator.comparingInt(day -> day.toDayOfWeek().getValue()))
            .map(Enum::name)
            .collect(Collectors.joining(","));
    }

    private CalendarEventSeriesCost seriesCost(EventCostDTO source, String actor) {
        CalendarEventSeriesCost cost = new CalendarEventSeriesCost();
        cost.setDescription(source.getDescription());
        cost.setAmount(source.getAmount());
        cost.initializeAudit(actor);
        return cost;
    }

    private EventCost eventCost(EventCostDTO source, String actor) {
        EventCost cost = new EventCost();
        cost.setDescription(source.getDescription());
        cost.setAmount(source.getAmount());
        cost.initializeAudit(actor);
        return cost;
    }

    private CalendarEventSeries findSeries(Long id) {
        return seriesRepository.findByIdAndDeletedFalse(id)
            .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Recurring series not found", "id.notFound"));
    }

    private CalendarEvents findSourceOccurrence(Long sourceOccurrenceId, List<CalendarEvents> events) {
        if (sourceOccurrenceId == null) return null;
        return events.stream()
            .filter(event -> Objects.equals(event.getId(), sourceOccurrenceId))
            .findFirst()
            .orElseThrow(() ->
                error(HttpStatus.BAD_REQUEST, "Source occurrence does not belong to the recurring series", "calendar.series.sourceOccurrence.invalid")
            );
    }

    private void rescheduleReminders(CalendarEvents event, AbstractAuthenticationToken token) {
        Map<String, Integer> availableUsers = new LinkedHashMap<>();
        event.getAvailabilities().stream()
            .filter(value -> value.getAvailability() == CalendarEventAvailability.Availability.AVAILABLE)
            .forEach(value -> availableUsers.put(value.getUser().getKeycloakId(), value.getReminderMinutes()));
        reminderProducer.rescheduleForAvailableUsers(eventMapper.toDto(event), availableUsers);
    }

    private void initialize(CommonFieldsOpenSearch entity, String actor) {
        Date now = new Date();
        entity.setDeleted(false);
        entity.setInsertBy(actor);
        entity.setInsertDate(now);
        entity.setEditBy(actor);
        entity.setEditDate(now);
    }

    private void touch(CommonFieldsOpenSearch entity, String actor) {
        entity.setEditBy(actor);
        entity.setEditDate(new Date());
    }

    private String actor(AbstractAuthenticationToken token) {
        return SecurityUtils.getUserIdFromAuthentication(token);
    }

    private boolean financeEnabled() {
        return tenantFeatureService.isEnabled(TenantFeature.FINANCE);
    }

    /**
     * From the source occurrence on, every position of the series takes the start that the changed rule gives it: it stays
     * the identity of the occurrence for its restore and for the changes of all the future occurrences. The positions no
     * longer generated and the other rows of a position free their start. The starts pass through a value owned by each
     * row, so that two rows never share one while they are exchanged.
     */
    private void realignOriginalStarts(
        Map<Integer, CalendarEvents> bySequence,
        List<CalendarEvents> events,
        int sourceSequence,
        List<ZonedDateTime> occurrences
    ) {
        Map<CalendarEvents, Instant> targets = new IdentityHashMap<>();
        for (CalendarEvents event : events) {
            Integer sequence = event.getSeriesSequence();
            if (sequence == null || sequence < sourceSequence) continue;
            boolean generated = sequence <= occurrences.size() && bySequence.get(sequence) == event;
            Instant target = generated ? occurrences.get(sequence - 1).toInstant() : parkedStart(event);
            if (!target.equals(event.getOriginalStartDate().toInstant())) targets.put(event, target);
        }
        if (targets.isEmpty()) return;
        targets.keySet().forEach(event -> event.setOriginalStartDate(Date.from(parkedStart(event))));
        saveOccurrences(targets.keySet());
        targets.forEach((event, target) -> event.setOriginalStartDate(Date.from(target)));
        saveOccurrences(targets.keySet());
    }

    private static Instant parkedStart(CalendarEvents event) {
        return Instant.EPOCH.minusSeconds(event.getId());
    }

    private void saveOccurrences(Collection<CalendarEvents> events) {
        try {
            eventRepository.saveAll(events);
            eventRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            if (metrics != null) metrics.recordFailure(CalendarSeriesMetrics.FailureReason.OCCURRENCE_CONFLICT);
            throw error(HttpStatus.CONFLICT, "Another occurrence of the series already starts there", "calendar.series.occurrence.conflict");
        }
    }

    /**
     * La chiave di idempotenza della creazione, normalizzata: assente quando il client non la manda,
     * perché le richieste vecchie restano valide.
     */
    private String idempotencyKey(CalendarEventSeriesRequest request) {
        String value = request == null ? null : request.getIdempotencyKey();
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > 64) {
            throw error(HttpStatus.BAD_REQUEST, "The idempotency key must be between 1 and 64 characters", "calendar.series.idempotencyKey.invalid");
        }
        return trimmed;
    }

    /**
     * Due richieste con la stessa chiave arrivate insieme: l'indice univoco ne lascia passare una, e
     * l'altra riceve un conflitto riconoscibile invece di creare una seconda serie.
     */
    private CalendarEventSeries saveSeries(CalendarEventSeries series, String idempotencyKey) {
        try {
            CalendarEventSeries saved = seriesRepository.save(series);
            seriesRepository.flush();
            return saved;
        } catch (DataIntegrityViolationException exception) {
            String cause = String.valueOf(exception.getMostSpecificCause().getMessage());
            if (idempotencyKey == null || !cause.contains("idempotency_key")) throw exception;
            if (metrics != null) metrics.recordFailure(CalendarSeriesMetrics.FailureReason.DUPLICATE_REQUEST);
            log.info("calendar_series_create_duplicate");
            throw error(HttpStatus.CONFLICT, "A series with the same idempotency key is already being created", "calendar.series.duplicateRequest");
        }
    }

    private RequestAlertException error(HttpStatus status, String message, String key) {
        return new RequestAlertException(status, message, "CalendarEventSeries", key);
    }

    private record SeriesDefinition(LocalDateTime firstStartLocal, int durationMinutes,
                                    List<ZonedDateTime> occurrences, List<YearMonth> skippedMonths) {
    }
}
