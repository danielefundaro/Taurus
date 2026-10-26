package com.fundaro.zodiac.taurus.service;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.PushReminder;
import com.fundaro.zodiac.taurus.domain.enumeration.TenantFeature;
import com.fundaro.zodiac.taurus.domain.notification.NotificationPreferencePolicy;
import com.fundaro.zodiac.taurus.domain.notification.NotificationPushPreview;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSource;
import com.fundaro.zodiac.taurus.domain.notification.NotificationStatus;
import com.fundaro.zodiac.taurus.multitenancy.TenantContext;
import com.fundaro.zodiac.taurus.repository.PushReminderRepository;
import com.fundaro.zodiac.taurus.service.notification.NotificationPreferenceDecision;
import com.fundaro.zodiac.taurus.service.notification.NotificationPreferenceMetrics;
import com.fundaro.zodiac.taurus.service.notification.NotificationPreferenceMetrics.QuietOutcome;
import com.fundaro.zodiac.taurus.service.notification.NotificationTiming;
import com.fundaro.zodiac.taurus.service.notification.PushDeliveryResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

/**
 * Consegna dei promemoria evento di {@code push_reminders}, un job per volta.
 *
 * <p>Ha la stessa forma del servizio delle altre consegne push: l'orchestratore
 * {@code NotificationPushScheduler} chiede gli ID pronti e poi tratta ogni job in una transazione
 * propria, con lock pessimista sulla riga. Così più repliche del backend non si rubano lo stesso
 * promemoria e il fallimento di uno non trascina gli altri.
 */
@Service
public class PushReminderDeliveryService {

    static final String ACTOR = "push-reminder-dispatcher";
    private static final String CALENDAR_EVENT_PATH = "/calendar/";
    private static final int MAX_ATTEMPTS = 8;

    private final PushReminderRepository reminderRepository;
    private final PushService pushService;
    private final ApplicationProperties.NotificationPushDeliveryProperties properties;
    private NotificationPreferenceResolver preferenceResolver;
    private NotificationPreferenceMetrics metrics;
    private TenantFeatureService tenantFeatureService;

    public PushReminderDeliveryService(
        PushReminderRepository reminderRepository,
        PushService pushService,
        ApplicationProperties applicationProperties
    ) {
        this.reminderRepository = reminderRepository;
        this.pushService = pushService;
        this.properties = applicationProperties.getNotificationPushDelivery();
    }

    @Autowired
    void setPreferenceResolver(NotificationPreferenceResolver value) {
        preferenceResolver = value;
    }

    @Autowired
    void setMetrics(NotificationPreferenceMetrics value) {
        metrics = value;
    }

    @Autowired
    void setTenantFeatureService(TenantFeatureService value) {
        tenantFeatureService = value;
    }

    @Transactional(propagation = Propagation.MANDATORY, readOnly = true)
    public List<Long> findReadyIds() {
        return reminderRepository.findReadyIds(
            NotificationStatus.PENDING,
            Instant.now(),
            PageRequest.of(0, Math.max(1, properties.getBatchSize()))
        );
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void process(long id) {
        PushReminder reminder = reminderRepository.findByIdForUpdate(id).orElse(null);
        if (reminder == null || reminder.getStatus() != NotificationStatus.PENDING) return;
        Instant now = Instant.now();
        if (reminder.getSendAt().isAfter(now)) return;

        if (tenantFeatureService != null && !tenantFeatureService.isEnabled(TenantFeature.WEB_PUSH_REMINDERS)) {
            skipped(reminder, "FEATURE_DISABLED");
            return;
        }
        // Un promemoria in ritardo per un fermo del backend non parte più a evento iniziato.
        if (eventStarted(reminder, now)) {
            skipped(reminder, "EVENT_STARTED");
            return;
        }
        if (preferenceResolver == null) {
            deliver(reminder, NotificationPushPreview.FULL, now);
            return;
        }

        NotificationPreferenceDecision preference = preferenceResolver.resolve(
            NotificationSource.CALENDAR,
            NotificationPreferencePolicy.CONFIGURABLE,
            Set.of(reminder.getUserId())
        ).get(reminder.getUserId());
        if (!preference.eventRemindersEnabled()) {
            skipped(reminder, "REMINDERS_DISABLED");
            return;
        }

        ZonedDateTime zonedNow = ZonedDateTime.now();
        ZonedDateTime allowed = NotificationTiming.nextAllowed(preference, zonedNow);
        if (allowed.isAfter(zonedNow.plusSeconds(1))) {
            if (eventStarted(reminder, allowed.toInstant())) {
                recordQuiet(QuietOutcome.SKIPPED);
                skipped(reminder, "EVENT_STARTED");
            } else {
                recordQuiet(QuietOutcome.DEFERRED);
                reminder.setSendAt(allowed.toInstant());
                reminder.setNextAttemptAt(allowed);
                reminder.touchAudit(ACTOR);
                reminderRepository.save(reminder);
            }
            return;
        }
        deliver(reminder, preference.pushPreview(), now);
    }

    private void deliver(PushReminder reminder, NotificationPushPreview preview, Instant now) {
        String title = preview == NotificationPushPreview.PRIVATE ? "Taurus" : "Promemoria evento";
        PushDeliveryResult result = pushService.sendToUserNow(
            reminder.getUserId(),
            TenantContext.getTenantCode().orElseThrow(),
            title,
            reminderBody(preview, reminder.getEventName(), reminder.getEventStartAt(), now),
            CALENDAR_EVENT_PATH + reminder.getEventId()
        );
        if (metrics != null) metrics.recordSubscriptionsRemoved(result.invalid());
        if (result.delivered()) {
            delivered(reminder);
        } else if (result.devices() == 0 || result.invalid() == result.devices()) {
            skipped(reminder, "NO_SUBSCRIPTION");
        } else if (result.retryable() && reminder.getAttempts() + 1 < MAX_ATTEMPTS) {
            retry(reminder, now);
        } else {
            failed(reminder);
        }
    }

    /**
     * Il corpo del promemoria. Con l'anteprima privata non compare nulla dell'evento; con quella
     * completa compaiono il nome e l'anticipo, che è l'informazione per cui il promemoria esiste.
     */
    static String reminderBody(NotificationPushPreview preview, String eventName, Instant eventStartAt, Instant now) {
        if (preview == NotificationPushPreview.PRIVATE) return "Hai un promemoria per un evento";
        String lead = leadTime(eventStartAt, now);
        return "L'evento \"" + eventName + "\" sta per iniziare" + (lead.isEmpty() ? "" : " " + lead);
    }

    /** L'anticipo in parole: minuti sotto l'ora, ore e minuti sopra. */
    static String leadTime(Instant eventStartAt, Instant now) {
        if (eventStartAt == null) return "";
        long minutes = Duration.between(now, eventStartAt).toMinutes();
        if (minutes < 1) return "adesso";
        if (minutes < 60) return "tra " + minutes + (minutes == 1 ? " minuto" : " minuti");
        long hours = minutes / 60;
        long rest = minutes % 60;
        String hoursText = "tra " + hours + (hours == 1 ? " ora" : " ore");
        return rest == 0 ? hoursText : hoursText + " e " + rest + (rest == 1 ? " minuto" : " minuti");
    }

    private static boolean eventStarted(PushReminder reminder, Instant moment) {
        return reminder.getEventStartAt() != null && !moment.isBefore(reminder.getEventStartAt());
    }

    private void retry(PushReminder reminder, Instant now) {
        if (metrics != null) metrics.recordRetry("EVENT_REMINDER", "TEMPORARY_PROVIDER_FAILURE");
        int attempts = reminder.getAttempts() + 1;
        reminder.setAttempts(attempts);
        ZonedDateTime retryAt = ZonedDateTime.now().plusMinutes(Math.min(60, 1L << Math.min(6, attempts - 1)));
        if (eventStarted(reminder, retryAt.toInstant())) {
            skipped(reminder, "EVENT_STARTED");
            return;
        }
        reminder.setSendAt(retryAt.toInstant());
        reminder.setNextAttemptAt(retryAt);
        reminder.setLastError("TEMPORARY_PROVIDER_FAILURE");
        reminder.touchAudit(ACTOR);
        reminderRepository.save(reminder);
    }

    /** Definitivo finché la console delle consegne non lo riapre. */
    private void failed(PushReminder reminder) {
        reminder.setStatus(NotificationStatus.FAILED);
        reminder.setNextAttemptAt(null);
        reminder.setAttempts(reminder.getAttempts() + 1);
        reminder.setLastError("PERMANENT_PROVIDER_FAILURE");
        recordReminderJob(NotificationStatus.FAILED);
        reminder.touchAudit(ACTOR);
        reminderRepository.save(reminder);
    }

    private void delivered(PushReminder reminder) {
        recordReminderJob(NotificationStatus.DELIVERED);
        reminder.setStatus(NotificationStatus.DELIVERED);
        reminder.setDeliveredAt(ZonedDateTime.now());
        reminder.setNextAttemptAt(null);
        reminder.setLastError(null);
        reminder.touchAudit(ACTOR);
        reminderRepository.save(reminder);
    }

    private void skipped(PushReminder reminder, String reason) {
        recordReminderJob(NotificationStatus.SKIPPED);
        reminder.setStatus(NotificationStatus.SKIPPED);
        reminder.setSkipReason(reason);
        reminder.setNextAttemptAt(null);
        reminder.setLastError(null);
        reminder.touchAudit(ACTOR);
        reminderRepository.save(reminder);
    }

    private void recordQuiet(QuietOutcome outcome) {
        if (metrics != null) metrics.recordQuietHoursReminder(outcome);
    }

    private void recordReminderJob(NotificationStatus status) {
        if (metrics != null) metrics.recordJob("EVENT_REMINDER", status);
    }
}
