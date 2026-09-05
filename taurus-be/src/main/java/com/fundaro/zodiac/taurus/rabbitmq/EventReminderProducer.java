package com.fundaro.zodiac.taurus.rabbitmq;

import com.fundaro.zodiac.taurus.domain.PushReminder;
import com.fundaro.zodiac.taurus.domain.criteria.PreferencesCriteria;
import com.fundaro.zodiac.taurus.repository.PushReminderRepository;
import com.fundaro.zodiac.taurus.service.PreferencesService;
import com.fundaro.zodiac.taurus.service.dto.CalendarEventsDTO;
import com.fundaro.zodiac.taurus.domain.notification.NotificationStatus;
import com.fundaro.zodiac.taurus.domain.notification.ReminderOrigin;
import com.fundaro.zodiac.taurus.repository.notification.NotificationProfileRepository;
import com.fundaro.zodiac.taurus.service.notification.NotificationPreferenceMetrics;
import org.springframework.beans.factory.annotation.Autowired;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
public class EventReminderProducer {

    private static final Logger log = LoggerFactory.getLogger(EventReminderProducer.class);
    private static final int DEFAULT_REMINDER_MINUTES = 30;

    private final PushReminderRepository reminderRepository;
    private final PreferencesService preferencesService;
    private NotificationProfileRepository notificationProfileRepository;
    private NotificationPreferenceMetrics metrics;

    public EventReminderProducer(PushReminderRepository reminderRepository, PreferencesService preferencesService) {
        this.reminderRepository = reminderRepository;
        this.preferencesService = preferencesService;
    }

    @Autowired
    void setNotificationProfileRepository(NotificationProfileRepository notificationProfileRepository) {
        this.notificationProfileRepository = notificationProfileRepository;
    }

    @Autowired
    void setMetrics(NotificationPreferenceMetrics metrics) {
        this.metrics = metrics;
    }

    public void scheduleIfNeeded(CalendarEventsDTO event, String userId, AbstractAuthenticationToken token) {
        scheduleIfNeeded(event, userId, null, token);
    }

    /**
     * Pianifica il promemoria di un singolo destinatario. Vince il valore
     * personale scelto sull'evento, poi quello dell'evento, poi la preferenza
     * dell'utente; zero disattiva il promemoria a qualunque livello sia scelto.
     */
    public void scheduleIfNeeded(CalendarEventsDTO event, String userId, Integer personalMinutes, AbstractAuthenticationToken token) {
        cancelPending(event.getId(), userId);
        if (event.getStartDate() == null) return;

        ResolvedReminder resolved = resolveReminder(personalMinutes, event.getReminderMinutes(), userId, token);
        if (resolved.minutes() <= 0) return;

        Instant sendAt = event.getStartDate().toInstant().minusSeconds((long) resolved.minutes() * 60);
        if (!sendAt.isAfter(Instant.now())) {
            log.debug("Event {} starts too soon for a reminder, skipping", event.getId());
            return;
        }

        PushReminder reminder = new PushReminder();
        reminder.initializeAudit(userId);
        reminder.setEventId(event.getId());
        reminder.setEventName(event.getName());
        reminder.setUserId(userId);
        reminder.setSendAt(sendAt);
        reminder.setSent(false);
        reminder.setEventStartAt(event.getStartDate().toInstant());
        reminder.setStatus(NotificationStatus.PENDING);
        reminder.setReminderOrigin(resolved.origin());
        reminder.setScheduleRevision(0);
        reminder.setAttempts(0);
        reminder.setNextAttemptAt(sendAt.atZone(java.time.ZoneOffset.UTC));

        reminderRepository.save(reminder);
        log.debug("Scheduled reminder for userId={} on event={} at {}", userId, event.getId(), sendAt);
    }

    public void cancelPending(Long eventId, String userId) {
        reminderRepository.deleteAllByEventIdAndUserIdAndSentFalse(eventId, userId);
    }

    public void cancelAllPending(Long eventId) {
        reminderRepository.deleteAllByEventIdAndSentFalse(eventId);
    }

    /**
     * @param availableUsers id Keycloak dei disponibili, con l'eventuale
     *                       promemoria personale scelto da ciascuno.
     */
    public void rescheduleForAvailableUsers(
        CalendarEventsDTO event,
        Map<String, Integer> availableUsers,
        AbstractAuthenticationToken token
    ) {
        cancelAllPending(event.getId());
        if (availableUsers == null) return;
        availableUsers.forEach((userId, personalMinutes) -> scheduleIfNeeded(event, userId, personalMinutes, token));
    }

    private ResolvedReminder resolveReminder(Integer personalMinutes, Integer eventMinutes, String userId, AbstractAuthenticationToken token) {
        if (personalMinutes != null) return new ResolvedReminder(personalMinutes, ReminderOrigin.PERSONAL);
        if (eventMinutes != null) return new ResolvedReminder(eventMinutes, ReminderOrigin.EVENT);
        if (notificationProfileRepository != null) {
            var profile = notificationProfileRepository.findByUserKeycloakIdAndDeletedFalse(userId).orElse(null);
            if (profile != null) {
                if (!profile.isEventRemindersEnabled()) return new ResolvedReminder(0, ReminderOrigin.PROFILE);
                return new ResolvedReminder(profile.getDefaultCalendarReminderMinutes(), ReminderOrigin.PROFILE);
            }
        }
        try {
            PreferencesCriteria criteria = new PreferencesCriteria();
            criteria.key().setEquals("defaultReminderMinutes");
            if (metrics != null) metrics.recordLegacyReminderRead();
            int legacy = preferencesService.findByCriteria(criteria, PageRequest.of(0, 1), token)
                .getContent().stream().findFirst()
                .map(p -> {
                    try { return Integer.parseInt(p.getValue()); } catch (NumberFormatException e) { return DEFAULT_REMINDER_MINUTES; }
                })
                .orElse(DEFAULT_REMINDER_MINUTES);
            return new ResolvedReminder(legacy, ReminderOrigin.APPLICATION);
        } catch (Exception e) {
            log.warn("Could not fetch defaultReminderMinutes preference, using default={}", DEFAULT_REMINDER_MINUTES);
            return new ResolvedReminder(DEFAULT_REMINDER_MINUTES, ReminderOrigin.APPLICATION);
        }
    }

    private record ResolvedReminder(int minutes, ReminderOrigin origin) {}
}
