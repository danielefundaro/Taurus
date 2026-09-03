package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import com.fundaro.zodiac.taurus.service.notification.NotificationCommand;
import org.springframework.stereotype.Service;

@Service
public class CrossTenantNotificationPublisher {

    private final TenantTransactionExecutor transactionExecutor;
    private final NotificationOutboxPublisher publisher;

    public CrossTenantNotificationPublisher(TenantTransactionExecutor transactionExecutor, NotificationOutboxPublisher publisher) {
        this.transactionExecutor = transactionExecutor;
        this.publisher = publisher;
    }

    public void enqueue(NotificationCommand command) {
        if (command == null || command.targetTenantCode() == null || command.targetTenantCode().isBlank()) {
            throw new IllegalArgumentException("targetTenantCode is required for a cross-tenant notification");
        }
        transactionExecutor.execute(command.targetTenantCode().trim(), () -> publisher.enqueue(command));
    }
}
