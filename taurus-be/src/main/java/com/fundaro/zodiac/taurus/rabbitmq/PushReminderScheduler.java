package com.fundaro.zodiac.taurus.rabbitmq;

import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaRegistry;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import com.fundaro.zodiac.taurus.multitenancy.TenantContext;
import com.fundaro.zodiac.taurus.repository.PushReminderRepository;
import com.fundaro.zodiac.taurus.service.PushService;
import com.fundaro.zodiac.taurus.service.NotificationPreferenceResolver;
import com.fundaro.zodiac.taurus.service.notification.NotificationPreferenceMetrics;
import com.fundaro.zodiac.taurus.service.notification.NotificationPreferenceMetrics.QuietOutcome;
import com.fundaro.zodiac.taurus.service.notification.NotificationTiming;
import com.fundaro.zodiac.taurus.domain.notification.NotificationPreferencePolicy;
import com.fundaro.zodiac.taurus.domain.notification.NotificationPushPreview;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSource;
import com.fundaro.zodiac.taurus.domain.notification.NotificationStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class PushReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(PushReminderScheduler.class);

    private final PushReminderRepository reminderRepository;
    private final PushService pushService;
    private final TenantSchemaRegistry tenantSchemaRegistry;
    private final TenantTransactionExecutor tenantTransactionExecutor;
    private NotificationPreferenceResolver preferenceResolver;
    private NotificationPreferenceMetrics metrics;

    public PushReminderScheduler(
        PushReminderRepository reminderRepository,
        PushService pushService,
        TenantSchemaRegistry tenantSchemaRegistry,
        TenantTransactionExecutor tenantTransactionExecutor
    ) {
        this.reminderRepository = reminderRepository;
        this.pushService = pushService;
        this.tenantSchemaRegistry = tenantSchemaRegistry;
        this.tenantTransactionExecutor = tenantTransactionExecutor;
    }

    @Autowired
    void setPreferenceResolver(NotificationPreferenceResolver preferenceResolver) {
        this.preferenceResolver = preferenceResolver;
    }

    @Autowired
    void setMetrics(NotificationPreferenceMetrics metrics) {
        this.metrics = metrics;
    }

    @Scheduled(cron = "0 * * * * *")
    public void processReminders() {
        tenantSchemaRegistry.findActiveTenantCodes().forEach(tenantCode ->
            tenantTransactionExecutor.execute(tenantCode, this::processCurrentTenantReminders)
        );
    }

    private void processCurrentTenantReminders() {
        reminderRepository.findByDeletedFalseAndSentFalseAndSendAtLessThanEqual(Instant.now()).forEach(reminder -> {
            if (preferenceResolver == null) {
                String body = String.format("L'evento \"%s\" sta per iniziare", reminder.getEventName());
                pushService.sendToUser(reminder.getUserId(), TenantContext.getTenantCode().orElseThrow(), "Promemoria evento", body);
                delivered(reminder);
                return;
            }
            var preference = preferenceResolver.resolve(
                NotificationSource.CALENDAR,
                NotificationPreferencePolicy.CONFIGURABLE,
                java.util.Set.of(reminder.getUserId())
            ).get(reminder.getUserId());
            if (!preference.eventRemindersEnabled()) {
                skipped(reminder, "REMINDERS_DISABLED");
                return;
            }
            var now = java.time.ZonedDateTime.now();
            var allowed = NotificationTiming.nextAllowed(preference, now);
            if (allowed.isAfter(now.plusSeconds(1))) {
                if (reminder.getEventStartAt() != null && !allowed.toInstant().isBefore(reminder.getEventStartAt())) {
                    recordQuiet(QuietOutcome.SKIPPED);
                    skipped(reminder, "EVENT_STARTED");
                } else {
                    recordQuiet(QuietOutcome.DEFERRED);
                    reminder.setSendAt(allowed.toInstant());
                    reminder.setNextAttemptAt(allowed);
                    reminder.touchAudit("push-reminder-scheduler");
                    reminderRepository.save(reminder);
                }
                return;
            }
            String body = String.format("L'evento \"%s\" sta per iniziare", reminder.getEventName());
            String title = preference.pushPreview() == NotificationPushPreview.PRIVATE ? "Taurus" : "Promemoria evento";
            if (preference.pushPreview() == NotificationPushPreview.PRIVATE) body = "Hai un promemoria per un evento";
            var result = pushService.sendToUserNow(
                reminder.getUserId(), TenantContext.getTenantCode().orElseThrow(), title, body, "/calendar-events/" + reminder.getEventId()
            );
            if (metrics != null) metrics.recordSubscriptionsRemoved(result.invalid());
            if (result.delivered()) {
                delivered(reminder);
            } else if (result.devices() == 0 || result.invalid() == result.devices()) {
                skipped(reminder, "NO_SUBSCRIPTION");
            } else if (result.retryable() && reminder.getAttempts() + 1 < 8) {
                if (metrics != null) metrics.recordRetry("EVENT_REMINDER", "TEMPORARY_PROVIDER_FAILURE");
                int attempts = reminder.getAttempts() + 1;
                reminder.setAttempts(attempts);
                var retryAt = now.plusMinutes(Math.min(60, 1L << Math.min(6, attempts - 1)));
                if (reminder.getEventStartAt() != null && !retryAt.toInstant().isBefore(reminder.getEventStartAt())) {
                    skipped(reminder, "EVENT_STARTED");
                } else {
                    reminder.setSendAt(retryAt.toInstant());
                    reminder.setNextAttemptAt(retryAt);
                    reminder.setLastError("TEMPORARY_PROVIDER_FAILURE");
                    reminder.touchAudit("push-reminder-scheduler");
                    reminderRepository.save(reminder);
                }
            } else {
                reminder.setStatus(NotificationStatus.FAILED);
                reminder.setAttempts(reminder.getAttempts() + 1);
                reminder.setLastError("PERMANENT_PROVIDER_FAILURE");
                recordReminderJob(NotificationStatus.FAILED);
                reminder.touchAudit("push-reminder-scheduler");
                reminderRepository.save(reminder);
            }
        });
    }

    private void recordQuiet(QuietOutcome outcome) {
        if (metrics != null) metrics.recordQuietHoursReminder(outcome);
    }

    private void recordReminderJob(NotificationStatus status) {
        if (metrics != null) metrics.recordJob("EVENT_REMINDER", status);
    }

    private void delivered(com.fundaro.zodiac.taurus.domain.PushReminder reminder) {
        recordReminderJob(NotificationStatus.DELIVERED);
        reminder.setSent(true);
        reminder.setStatus(NotificationStatus.DELIVERED);
        reminder.setDeliveredAt(java.time.ZonedDateTime.now());
        reminder.setNextAttemptAt(null);
        reminder.setLastError(null);
        reminder.touchAudit("push-reminder-scheduler");
        reminderRepository.save(reminder);
    }

    private void skipped(com.fundaro.zodiac.taurus.domain.PushReminder reminder, String reason) {
        recordReminderJob(NotificationStatus.SKIPPED);
        reminder.setSent(true);
        reminder.setStatus(NotificationStatus.SKIPPED);
        reminder.setSkipReason(reason);
        reminder.setNextAttemptAt(null);
        reminder.setLastError(null);
        reminder.touchAudit("push-reminder-scheduler");
        reminderRepository.save(reminder);
    }
}
