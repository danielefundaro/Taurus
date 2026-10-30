package com.fundaro.zodiac.taurus.service.calendarfeed;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

/**
 * Downloads, generation and maintenance of the external calendar feeds.
 *
 * <p>The labels are closed — the outcome, the kind of feed, its scope, the limit that fired — and
 * none of them carries a feed, a tenant, a user, an event UID, a fingerprint or a token: those
 * would multiply the time series and would put a credential's trail into the metrics. The share of
 * {@code 304} responses is read from the {@code outcome} label of the requests, not from a counter
 * of its own.
 */
@Component
public class CalendarFeedMetrics {

    private static final String PREFIX = "taurus.calendar_feed.";

    private final MeterRegistry registry;

    public CalendarFeedMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** One download that ended: {@code 200}, {@code 304}, {@code 404}, {@code 429} or {@code 503}. */
    public void request(String outcome, String feedType, String scope, long durationMillis) {
        registry.counter(PREFIX + "requests", "outcome", tag(outcome), "type", tag(feedType), "scope", tag(scope)).increment();
        Timer.builder(PREFIX + "request.duration").tag("outcome", tag(outcome)).register(registry)
            .record(durationMillis, TimeUnit.MILLISECONDS);
    }

    /** From the token to the subscription: registry, tenant schema and consistency checks. */
    public void resolution(String outcome, long durationMillis) {
        Timer.builder(PREFIX + "resolution.duration").tag("outcome", tag(outcome)).register(registry)
            .record(durationMillis, TimeUnit.MILLISECONDS);
    }

    /** The iCalendar representation produced, with the components it carries. */
    public void generation(long durationMillis, int components) {
        Timer.builder(PREFIX + "generation.duration").register(registry).record(durationMillis, TimeUnit.MILLISECONDS);
        summary("components").record(components);
    }

    /** A representation that could not be produced: {@code render} or {@code limit}. */
    public void generationError(String kind) {
        registry.counter(PREFIX + "generation.errors", "kind", tag(kind)).increment();
    }

    /** A request stopped before the database: {@code token}, {@code ip} or {@code global}. */
    public void rateLimited(String scope) {
        registry.counter(PREFIX + "rate.limited", "scope", tag(scope)).increment();
    }

    /** A lifecycle command: {@code created}, {@code rotated}, {@code revoked} or {@code removed}. */
    public void operation(String action, String outcome) {
        registry.counter(PREFIX + "operations", "action", tag(action), "outcome", tag(outcome)).increment();
    }

    /** Tombstones written by the event lifecycle or dropped by the retention job. */
    public void tombstones(String operation, long count) {
        if (count > 0) registry.counter(PREFIX + "tombstones", "operation", tag(operation)).increment(count);
    }

    /** Idempotency receipts dropped by the retention job. */
    public void receiptsDeleted(long count) {
        if (count > 0) registry.counter(PREFIX + "receipts.deleted").increment(count);
    }

    /**
     * Global rows that no longer match their tenant schema: the alert watches this counter, because
     * an active credential without a live subscription is a credential nobody can see any more.
     */
    public void registryOrphans(String outcome, long count) {
        if (count > 0) registry.counter(PREFIX + "registry.orphans", "outcome", tag(outcome)).increment(count);
    }

    private DistributionSummary summary(String name) {
        return DistributionSummary.builder(PREFIX + name).publishPercentileHistogram().register(registry);
    }

    private static String tag(String value) {
        return value == null || value.isBlank() ? "unknown" : value.toLowerCase(Locale.ROOT);
    }
}
