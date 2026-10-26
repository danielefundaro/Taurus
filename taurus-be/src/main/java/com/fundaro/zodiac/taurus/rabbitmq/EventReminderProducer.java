package com.fundaro.zodiac.taurus.rabbitmq;

import com.fundaro.zodiac.taurus.domain.PushReminder;
import com.fundaro.zodiac.taurus.domain.enumeration.TenantFeature;
import com.fundaro.zodiac.taurus.domain.notification.NotificationStatus;
import com.fundaro.zodiac.taurus.domain.notification.ReminderOrigin;
import com.fundaro.zodiac.taurus.repository.PushReminderRepository;
import com.fundaro.zodiac.taurus.repository.notification.NotificationProfileRepository;
import com.fundaro.zodiac.taurus.service.TenantFeatureService;
import com.fundaro.zodiac.taurus.service.dto.CalendarEventsDTO;
import com.fundaro.zodiac.taurus.service.notification.NotificationPreferenceMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;

@Service
public class EventReminderProducer {

    private static final Logger log = LoggerFactory.getLogger(EventReminderProducer.class);
    private static final int DEFAULT_REMINDER_MINUTES = 30;
    /** Le origini che seguono il valore predefinito del profilo, le sole che la ripianificazione rifà. */
    private static final java.util.Set<ReminderOrigin> INHERITED_ORIGINS =
        java.util.EnumSet.of(ReminderOrigin.PROFILE, ReminderOrigin.APPLICATION);

    private final PushReminderRepository reminderRepository;
    private NotificationProfileRepository notificationProfileRepository;
    private TenantFeatureService tenantFeatureService;

    public EventReminderProducer(PushReminderRepository reminderRepository) {
        this.reminderRepository = reminderRepository;
    }

    @Autowired
    void setNotificationProfileRepository(NotificationProfileRepository notificationProfileRepository) {
        this.notificationProfileRepository = notificationProfileRepository;
    }

    @Autowired
    void setTenantFeatureService(TenantFeatureService value) {
        tenantFeatureService = value;
    }

    public void scheduleIfNeeded(CalendarEventsDTO event, String userId) {
        scheduleIfNeeded(event, userId, null);
    }

    /**
     * Pianifica il promemoria di un singolo destinatario. Vince il valore
     * personale scelto sull'evento, poi quello dell'evento, poi la preferenza
     * dell'utente; zero disattiva il promemoria a qualunque livello sia scelto.
     */
    public void scheduleIfNeeded(CalendarEventsDTO event, String userId, Integer personalMinutes) {
        cancelPending(event.getId(), userId);
        schedule(event, userId, personalMinutes, 0);
    }

    /**
     * Rifà il promemoria di un'occorrenza dopo un cambio del valore predefinito personale.
     *
     * <p>Toccare solo i job che ereditano quel valore è la differenza che conta: un anticipo scelto
     * sull'occorrenza o sull'evento è una decisione dell'utente, e un job già fallito appartiene alla
     * console delle consegne. La revisione cresce di uno a ogni rifacimento, così la storia del job
     * resta leggibile.
     *
     * @return vero se il promemoria è stato rifatto, falso se l'occorrenza ha un anticipo proprio.
     */
    public boolean rescheduleInherited(CalendarEventsDTO event, String userId, Integer personalMinutes) {
        if (personalMinutes != null || event.getReminderMinutes() != null) return false;

        int revision = reminderRepository
            .findAllByEventIdAndUserIdAndStatusAndDeletedFalse(event.getId(), userId, NotificationStatus.PENDING)
            .stream()
            .filter(reminder -> INHERITED_ORIGINS.contains(reminder.getReminderOrigin()))
            .mapToInt(PushReminder::getScheduleRevision)
            .max()
            .orElse(-1) + 1;
        reminderRepository.deleteAllByEventIdAndUserIdAndStatusAndReminderOriginIn(
            event.getId(), userId, NotificationStatus.PENDING, INHERITED_ORIGINS
        );
        schedule(event, userId, null, revision);
        return true;
    }

    /** Chiude il lotto corrente della ripianificazione sul database. */
    public void flush() {
        reminderRepository.flush();
    }

    private void schedule(CalendarEventsDTO event, String userId, Integer personalMinutes, int scheduleRevision) {
        if (tenantFeatureService != null && !tenantFeatureService.isEnabled(TenantFeature.WEB_PUSH_REMINDERS)) return;
        if (event.getStartDate() == null) return;

        ResolvedReminder resolved = resolveReminder(personalMinutes, event.getReminderMinutes(), userId);
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
        reminder.setEventStartAt(event.getStartDate().toInstant());
        reminder.setStatus(NotificationStatus.PENDING);
        reminder.setReminderOrigin(resolved.origin());
        reminder.setScheduleRevision(scheduleRevision);
        reminder.setAttempts(0);
        reminder.setNextAttemptAt(sendAt.atZone(java.time.ZoneOffset.UTC));

        reminderRepository.save(reminder);
        // Nessun identificativo del socio nei log, nemmeno in debug: l'evento basta a capire cosa è stato pianificato.
        log.debug("Scheduled reminder on event={} at {}", event.getId(), sendAt);
    }

    public void cancelPending(Long eventId, String userId) {
        reminderRepository.deleteAllByEventIdAndUserIdAndStatus(eventId, userId, NotificationStatus.PENDING);
    }

    public void cancelAllPending(Long eventId) {
        reminderRepository.deleteAllByEventIdAndStatus(eventId, NotificationStatus.PENDING);
    }

    /**
     * @param availableUsers id Keycloak dei disponibili, con l'eventuale
     *                       promemoria personale scelto da ciascuno.
     */
    public void rescheduleForAvailableUsers(CalendarEventsDTO event, Map<String, Integer> availableUsers) {
        cancelAllPending(event.getId());
        if (availableUsers == null) return;
        availableUsers.forEach((userId, personalMinutes) -> scheduleIfNeeded(event, userId, personalMinutes));
    }

    private ResolvedReminder resolveReminder(Integer personalMinutes, Integer eventMinutes, String userId) {
        var profile = notificationProfileRepository == null
            ? null
            : notificationProfileRepository.findByKeycloakSubjectAndDeletedFalse(userId).orElse(null);
        // L'interruttore del profilo è globale: spento, non si pianifica nulla, nemmeno un anticipo
        // scelto sull'occorrenza o sull'evento.
        if (profile != null && !profile.isEventRemindersEnabled()) return new ResolvedReminder(0, ReminderOrigin.PROFILE);
        if (personalMinutes != null) return new ResolvedReminder(personalMinutes, ReminderOrigin.PERSONAL);
        if (eventMinutes != null) return new ResolvedReminder(eventMinutes, ReminderOrigin.EVENT);
        if (profile != null) return new ResolvedReminder(profile.getDefaultCalendarReminderMinutes(), ReminderOrigin.PROFILE);
        // Senza profilo vale il default applicativo: la vecchia preferenza chiave-valore è stata
        // migrata nei profili e non viene più letta.
        return new ResolvedReminder(DEFAULT_REMINDER_MINUTES, ReminderOrigin.APPLICATION);
    }

    private record ResolvedReminder(int minutes, ReminderOrigin origin) {
    }
}
