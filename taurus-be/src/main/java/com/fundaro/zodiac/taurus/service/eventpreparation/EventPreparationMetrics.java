package com.fundaro.zodiac.taurus.service.eventpreparation;

import com.fundaro.zodiac.taurus.service.dto.eventpreparation.EventPreparationDtos.Evaluation;
import com.fundaro.zodiac.taurus.service.dto.eventpreparation.EventPreparationDtos.Issue;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

/**
 * Evaluations, issues, commands and invalidated confirmations of the event preparation.
 *
 * <p>The tags are closed — status, phase, view, area, severity, code, outcome — because an event, a tenant, a user,
 * a track or an item as a tag would multiply the time series and turn the metrics into a history of the events.
 */
@Component
public class EventPreparationMetrics {

    private static final String PREFIX = "taurus.event_preparation.";

    private final MeterRegistry registry;

    public EventPreparationMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** One evaluated event, with every issue it lists. */
    public void evaluated(Evaluation evaluation) {
        registry.counter(PREFIX + "evaluation", "status", tag(evaluation.preparationStatus().name()), "closure", tag(evaluation.closureStatus().name()),
            "phase", tag(evaluation.phase().name())).increment();
        for (Issue issue : evaluation.issues())
            registry.counter(PREFIX + "issue", "area", tag(issue.area()), "severity", tag(issue.severity().name()), "code", tag(issue.code())).increment();
    }

    /** How long a view took: {@code admin}, {@code catalogue}, {@code personal}, {@code finance}, {@code dashboard} or {@code scheduler}. */
    public void duration(String view, long durationNanos) {
        Timer.builder(PREFIX + "evaluation.duration").tag("view", tag(view)).register(registry).record(durationNanos, TimeUnit.NANOSECONDS);
    }

    /** A command: {@code success}, {@code conflict}, {@code invalid}, {@code not_found} or {@code error}. */
    public void command(String area, String outcome) {
        registry.counter(PREFIX + "command", "area", tag(area), "outcome", tag(outcome)).increment();
    }

    /** A confirmation found obsolete for the first time. */
    public void confirmationInvalidated(String area) {
        registry.counter(PREFIX + "confirmation.invalidated", "area", tag(area)).increment();
    }

    private static String tag(String value) {
        return value == null || value.isBlank() ? "unknown" : value.toLowerCase(Locale.ROOT);
    }
}
