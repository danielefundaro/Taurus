package com.fundaro.zodiac.taurus.service;

import com.fundaro.zodiac.taurus.service.dto.UxTelemetryDTO;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

/**
 * Usage counters reported by the frontend, turned into Micrometer meters.
 *
 * <p>The tags come from a browser, so they are whitelisted twice: the feature must be a known one
 * and the event name must look like an identifier. Anything else is dropped, together with the
 * number of events, the counts and the duration, which are all capped: an exploded tag cardinality
 * would cost more than the measure is worth, and a telemetry report must never be able to damage
 * the metrics of an installation. Nothing here carries file names, part names or searched text.
 */
@Component
public class UxTelemetryMetrics {

    static final Set<String> FEATURES = Set.of("track-parts-workspace");
    private static final String PREFIX = "taurus.ux.";
    private static final Pattern EVENT = Pattern.compile("^[a-z][a-z-]{0,23}$");
    private static final int MAX_EVENTS = 20;
    private static final int MAX_COUNT = 1_000;
    private static final long MAX_DURATION_MS = Duration.ofHours(8).toMillis();

    private final MeterRegistry registry;

    public UxTelemetryMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** Records what the report contains, and answers whether it was a report of a known feature. */
    public boolean record(UxTelemetryDTO report) {
        if (report == null || report.feature() == null || !FEATURES.contains(report.feature())) return false;
        Map<String, Integer> events = report.events() == null ? Map.of() : report.events();
        events.entrySet().stream()
            .filter(event -> EVENT.matcher(event.getKey()).matches())
            .filter(event -> event.getValue() != null && event.getValue() > 0)
            .limit(MAX_EVENTS)
            .forEach(event -> registry
                .counter(PREFIX + "events", "feature", report.feature(), "event", event.getKey())
                .increment(Math.min(event.getValue(), MAX_COUNT)));
        Timer.builder(PREFIX + "session.duration")
            .tag("feature", report.feature())
            .register(registry)
            .record(Math.max(0, Math.min(report.durationMs(), MAX_DURATION_MS)), TimeUnit.MILLISECONDS);
        return true;
    }
}
