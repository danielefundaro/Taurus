package com.fundaro.zodiac.taurus.rabbitmq;

import com.fundaro.zodiac.taurus.repository.PushReminderRepository;
import com.fundaro.zodiac.taurus.service.PushService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class PushReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(PushReminderScheduler.class);

    private final PushReminderRepository reminderRepository;
    private final PushService pushService;

    public PushReminderScheduler(PushReminderRepository reminderRepository, PushService pushService) {
        this.reminderRepository = reminderRepository;
        this.pushService = pushService;
    }

    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void processReminders() {
        reminderRepository.findBySentFalseAndSendAtLessThanEqual(Instant.now()).forEach(reminder -> {
            log.debug("Sending reminder for userId={}, event={}", reminder.getUserId(), reminder.getEventId());
            String body = String.format("L'evento \"%s\" sta per iniziare", reminder.getEventName());
            pushService.sendToUser(reminder.getUserId(), reminder.getTenantCode(), "Promemoria evento", body);
            reminder.setSent(true);
            reminderRepository.save(reminder);
        });
    }
}
