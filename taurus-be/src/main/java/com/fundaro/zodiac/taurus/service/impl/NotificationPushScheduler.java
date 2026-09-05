package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaRegistry;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import com.fundaro.zodiac.taurus.service.NotificationPushDeliveryService;
import com.fundaro.zodiac.taurus.service.notification.NotificationPreferenceMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationPushScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationPushScheduler.class);
    private final TenantSchemaRegistry tenantSchemaRegistry;
    private final TenantTransactionExecutor transactionExecutor;
    private final NotificationPushDeliveryService deliveryService;
    private final NotificationPreferenceMetrics metrics;

    public NotificationPushScheduler(
        TenantSchemaRegistry tenantSchemaRegistry,
        TenantTransactionExecutor transactionExecutor,
        NotificationPushDeliveryService deliveryService,
        NotificationPreferenceMetrics metrics
    ) {
        this.tenantSchemaRegistry = tenantSchemaRegistry;
        this.transactionExecutor = transactionExecutor;
        this.deliveryService = deliveryService;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${application.notification-push-delivery.poll-delay:5000}")
    public void dispatch() {
        for (String tenantCode : tenantSchemaRegistry.findActiveTenantCodes()) {
            long startedAt = System.nanoTime();
            int processed = 0;
            for (Long id : transactionExecutor.execute(tenantCode, deliveryService::findReadyIds)) {
                processed++;
                try {
                    transactionExecutor.execute(tenantCode, () -> deliveryService.process(id));
                } catch (RuntimeException exception) {
                    LOG.warn("Notification push job failed tenant={} jobId={} errorClass={}", tenantCode, id, exception.getClass().getSimpleName());
                }
            }
            if (processed > 0) metrics.recordBatch("push", processed, System.nanoTime() - startedAt);
        }
    }
}
