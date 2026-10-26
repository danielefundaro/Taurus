package com.fundaro.zodiac.taurus.service.calendar;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

/**
 * Metriche delle serie ricorrenti del calendario.
 *
 * <p>Nessun tag contiene tenant, identificativi o testi: solo enum a cardinalità nota, come
 * prescrive la specifica. Le descrizioni degli eventi non vengono mai registrate.
 */
@Component
public class CalendarSeriesMetrics {

    private static final String PREFIX = "taurus.calendar.series.";

    /** Che cosa è stato fatto alla serie. */
    public enum SeriesOperation {
        CREATED,
        UPDATED,
        DELETED
    }

    /** Che cosa è stato fatto alle occorrenze durante un'operazione massiva. */
    public enum OccurrenceOutcome {
        CREATED,
        UPDATED,
        DELETED,
        KEPT_EXCEPTION,
        SKIPPED_PAST,
        OUTSIDE_RULE_EXCEPTION
    }

    /** Perché una generazione non è andata a buon fine. */
    public enum FailureReason {
        LIMIT_EXCEEDED,
        INVALID_RULE,
        OCCURRENCE_CONFLICT,
        VERSION_CONFLICT,
        DUPLICATE_REQUEST
    }

    private final MeterRegistry registry;

    public CalendarSeriesMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordSeries(SeriesOperation operation) {
        registry.counter(PREFIX + "operations", "operation", operation.name()).increment();
    }

    public void recordOccurrences(OccurrenceOutcome outcome, int count) {
        if (count > 0) registry.counter(PREFIX + "occurrences", "outcome", outcome.name()).increment(count);
    }

    /** Durata di un'operazione che tocca molte occorrenze, con quante ne ha toccate. */
    public void recordBulk(SeriesOperation operation, int occurrences, long elapsedNanos) {
        DistributionSummary.builder(PREFIX + "bulk.occurrences").tag("operation", operation.name()).register(registry).record(occurrences);
        Timer.builder(PREFIX + "bulk.duration").tag("operation", operation.name()).register(registry).record(elapsedNanos, TimeUnit.NANOSECONDS);
    }

    public void recordFailure(FailureReason reason) {
        registry.counter(PREFIX + "failures", "reason", reason.name()).increment();
    }

    /** Promemoria ripianificati o cancellati da un riallineamento. */
    public void recordReminders(int rescheduled, int cancelled) {
        if (rescheduled > 0) registry.counter(PREFIX + "reminders", "action", "RESCHEDULED").increment(rescheduled);
        if (cancelled > 0) registry.counter(PREFIX + "reminders", "action", "CANCELLED").increment(cancelled);
    }

    /** Disponibilità impostata o azzerata in blocco su una serie. */
    public void recordBulkAvailability(int occurrences) {
        DistributionSummary.builder(PREFIX + "availability.occurrences").register(registry).record(occurrences);
    }
}
