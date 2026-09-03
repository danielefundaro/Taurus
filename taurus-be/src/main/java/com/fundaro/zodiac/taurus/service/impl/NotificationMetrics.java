package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.notification.NotificationOutbox;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSource;
import com.fundaro.zodiac.taurus.domain.notification.NotificationStatus;
import com.fundaro.zodiac.taurus.multitenancy.TenantContext;
import com.fundaro.zodiac.taurus.service.notification.NotificationPendingSummary;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

@Component
public class NotificationMetrics {

    private static final String PREFIX = "taurus.notifications.";
    private final MeterRegistry registry;
    private final Map<MetricKey, AtomicLong> pendingGauges = new ConcurrentHashMap<>();
    private final Map<MetricKey, AtomicLong> oldestGauges = new ConcurrentHashMap<>();

    public NotificationMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordPending(List<NotificationPendingSummary> summaries, ZonedDateTime now) {
        String tenant = tenant();
        Map<NotificationSource, NotificationPendingSummary> bySource = new EnumMap<>(NotificationSource.class);
        summaries.forEach(summary -> bySource.put(summary.getSource(), summary));
        for (NotificationSource source : NotificationSource.values()) {
            NotificationPendingSummary summary = bySource.get(source);
            setGauge(pendingGauges, PREFIX + "pending", tenant, source, summary == null ? 0 : summary.getPendingCount());
            long age = summary == null || summary.getOldestOccurredAt() == null
                ? 0
                : Math.max(0, Duration.between(summary.getOldestOccurredAt(), now).toSeconds());
            setGauge(oldestGauges, PREFIX + "oldest.pending.seconds", tenant, source, age);
        }
    }

    public void recordAttempt(NotificationOutbox event) {
        registry.counter(PREFIX + "attempts", tags(event)).increment();
    }

    public void recordDelivered(NotificationOutbox event, int recipients, ZonedDateTime deliveredAt) {
        registry.counter(PREFIX + "delivered", tags(event)).increment();
        DistributionSummary.builder(PREFIX + "recipients")
            .tags(tags(event))
            .register(registry)
            .record(recipients);
        Timer.builder(PREFIX + "delivery.latency")
            .tags(tags(event))
            .register(registry)
            .record(Math.max(0, Duration.between(event.getOccurredAt(), deliveredAt).toNanos()), TimeUnit.NANOSECONDS);
    }

    public void recordFailure(NotificationOutbox event) {
        registry.counter(PREFIX + (event.getStatus() == NotificationStatus.FAILED ? "failed" : "retry.scheduled"), tags(event)).increment();
    }

    public void recordSchedulerDuration(String tenant, long elapsedNanos) {
        Timer.builder(PREFIX + "scheduler.duration")
            .tag("tenant", tenant)
            .register(registry)
            .record(elapsedNanos, TimeUnit.NANOSECONDS);
    }

    private void setGauge(
        Map<MetricKey, AtomicLong> gauges,
        String name,
        String tenant,
        NotificationSource source,
        long value
    ) {
        MetricKey key = new MetricKey(tenant, source);
        gauges.computeIfAbsent(key, ignored -> registry.gauge(
            name,
            List.of(io.micrometer.core.instrument.Tag.of("tenant", tenant), io.micrometer.core.instrument.Tag.of("source", source.name())),
            new AtomicLong()
        )).set(value);
    }

    private String[] tags(NotificationOutbox event) {
        return new String[] { "tenant", tenant(), "source", event.getSource().name(), "operation", event.getOperation() };
    }

    private static String tenant() {
        return TenantContext.getTenantCode().orElse("unknown");
    }

    private record MetricKey(String tenant, NotificationSource source) {}
}
