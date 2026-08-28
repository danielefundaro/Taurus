package com.fundaro.zodiac.taurus.rabbitmq;

import com.fundaro.zodiac.taurus.domain.PushReminder;
import com.fundaro.zodiac.taurus.domain.criteria.PreferencesCriteria;
import com.fundaro.zodiac.taurus.repository.PushReminderRepository;
import com.fundaro.zodiac.taurus.service.PreferencesService;
import com.fundaro.zodiac.taurus.service.dto.CalendarEventsDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class EventReminderProducer {

    private static final Logger log = LoggerFactory.getLogger(EventReminderProducer.class);
    private static final int DEFAULT_REMINDER_MINUTES = 60;

    private final PushReminderRepository reminderRepository;
    private final PreferencesService preferencesService;

    public EventReminderProducer(PushReminderRepository reminderRepository, PreferencesService preferencesService) {
        this.reminderRepository = reminderRepository;
        this.preferencesService = preferencesService;
    }

    public void scheduleIfNeeded(CalendarEventsDTO event, String userId, AbstractAuthenticationToken token) {
        cancelPending(event.getId(), userId);
        if (event.getStartDate() == null) return;

        int minutes = resolveReminderMinutes(event.getReminderMinutes(), token);
        if (minutes <= 0) return;

        Instant sendAt = event.getStartDate().toInstant().minusSeconds((long) minutes * 60);
        if (!sendAt.isAfter(Instant.now())) {
            log.debug("Event {} starts too soon for a reminder, skipping", event.getId());
            return;
        }

        PushReminder reminder = new PushReminder();
        reminder.setEventId(event.getId());
        reminder.setEventName(event.getName());
        reminder.setUserId(userId);
        reminder.setSendAt(sendAt);
        reminder.setSent(false);

        reminderRepository.save(reminder);
        log.debug("Scheduled reminder for userId={} on event={} at {}", userId, event.getId(), sendAt);
    }

    public void cancelPending(Long eventId, String userId) {
        reminderRepository.deleteAllByEventIdAndUserIdAndSentFalse(eventId, userId);
    }

    private int resolveReminderMinutes(Integer reminderMinutes, AbstractAuthenticationToken token) {
        if (reminderMinutes != null && reminderMinutes == 0) return 0;
        if (reminderMinutes != null && reminderMinutes > 0) return reminderMinutes;
        try {
            PreferencesCriteria criteria = new PreferencesCriteria();
            criteria.key().setEquals("defaultReminderMinutes");
            return preferencesService.findByCriteria(criteria, PageRequest.of(0, 1), token)
                .getContent().stream().findFirst()
                .map(p -> {
                    try { return Integer.parseInt(p.getValue()); } catch (NumberFormatException e) { return DEFAULT_REMINDER_MINUTES; }
                })
                .orElse(DEFAULT_REMINDER_MINUTES);
        } catch (Exception e) {
            log.warn("Could not fetch defaultReminderMinutes preference, using default={}", DEFAULT_REMINDER_MINUTES);
            return DEFAULT_REMINDER_MINUTES;
        }
    }
}
