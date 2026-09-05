package com.fundaro.zodiac.taurus.service.notification;

import com.fundaro.zodiac.taurus.domain.notification.NotificationSource;
import com.fundaro.zodiac.taurus.domain.notification.NotificationStatus;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

/**
 * Metriche delle preferenze notifiche e delle code push.
 *
 * <p>A differenza di {@code NotificationMetrics}, che precede questa specifica,
 * qui nessun tag contiene tenant, utente, ID notifica, chiave evento o endpoint:
 * i tag ammessi sono soltanto enum a cardinalità nota.
 */
@Component
public class NotificationPreferenceMetrics {

    private static final String PREFIX = "taurus.notification.preferences.";

    /** Esito della decisione di canale durante il fan-out. */
    public enum FanoutResult {
        DELIVERED,
        SUPPRESSED,
        REQUIRED_OVERRIDE
    }

    /** Canale valutato durante il fan-out. */
    public enum FanoutChannel {
        IN_APP,
        PUSH
    }

    /** Esito di un promemoria che cade dentro ore silenziose o pausa. */
    public enum QuietOutcome {
        DEFERRED,
        SKIPPED
    }

    private final MeterRegistry registry;

    public NotificationPreferenceMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void recordFanoutDecision(NotificationSource source, FanoutChannel channel, FanoutResult result) {
        registry.counter(
            PREFIX + "fanout.decisions",
            "source", source.name(),
            "channel", channel.name(),
            "result", result.name()
        ).increment();
    }

    public void recordJob(String deliveryType, NotificationStatus status) {
        registry.counter(PREFIX + "push.jobs", "type", deliveryType, "status", status.name()).increment();
    }

    public void recordDigestSize(int items) {
        DistributionSummary.builder(PREFIX + "digest.items").register(registry).record(items);
    }

    /** Ritardo tra l'istante pianificato e il tentativo effettivo. */
    public void recordDispatchDelay(String deliveryType, ZonedDateTime scheduledAt, ZonedDateTime attemptedAt) {
        if (scheduledAt == null || attemptedAt == null) return;
        Timer.builder(PREFIX + "push.delay")
            .tag("type", deliveryType)
            .register(registry)
            .record(Math.max(0, Duration.between(scheduledAt, attemptedAt).toNanos()), TimeUnit.NANOSECONDS);
    }

    public void recordRetry(String deliveryType, String errorClass) {
        registry.counter(PREFIX + "push.retries", "type", deliveryType, "error", errorClass).increment();
    }

    public void recordBatch(String queue, int size, long elapsedNanos) {
        DistributionSummary.builder(PREFIX + "batch.size").tag("queue", queue).register(registry).record(size);
        Timer.builder(PREFIX + "batch.duration").tag("queue", queue).register(registry).record(elapsedNanos, TimeUnit.NANOSECONDS);
    }

    public void recordQuietHoursReminder(QuietOutcome outcome) {
        registry.counter(PREFIX + "reminders.quiet", "outcome", outcome.name()).increment();
    }

    /** Sottoscrizioni rimosse perché il provider ha risposto {@code 404} o {@code 410}. */
    public void recordSubscriptionsRemoved(int count) {
        if (count > 0) registry.counter(PREFIX + "subscriptions.removed").increment(count);
    }

    public void recordProfileConflict() {
        registry.counter(PREFIX + "profile.conflicts").increment();
    }

    /** Righe legacy {@code defaultReminderMinutes} ancora lette durante la migrazione. */
    public void recordLegacyReminderRead() {
        registry.counter(PREFIX + "legacy.reminder.reads").increment();
    }
}
