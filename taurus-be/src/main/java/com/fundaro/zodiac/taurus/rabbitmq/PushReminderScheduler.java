package com.fundaro.zodiac.taurus.rabbitmq;

import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaRegistry;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import com.fundaro.zodiac.taurus.multitenancy.TenantContext;
import com.fundaro.zodiac.taurus.repository.PushReminderRepository;
import com.fundaro.zodiac.taurus.service.PushService;
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

    @Scheduled(cron = "0 * * * * *")
    public void processReminders() {
        tenantSchemaRegistry.findActiveTenantCodes().forEach(tenantCode ->
            tenantTransactionExecutor.execute(tenantCode, this::processCurrentTenantReminders)
        );
    }

    private void processCurrentTenantReminders() {
        reminderRepository.findByDeletedFalseAndSentFalseAndSendAtLessThanEqual(Instant.now()).forEach(reminder -> {
            log.debug("Sending reminder for userId={}, event={}", reminder.getUserId(), reminder.getEventId());
            String body = String.format("L'evento \"%s\" sta per iniziare", reminder.getEventName());
            pushService.sendToUser(reminder.getUserId(), TenantContext.getTenantCode().orElseThrow(), "Promemoria evento", body);
            reminder.setSent(true);
            reminderRepository.save(reminder);
        });
    }
}
