package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaRegistry;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import com.fundaro.zodiac.taurus.service.NotificationPushDeliveryService;
import com.fundaro.zodiac.taurus.service.PushReminderDeliveryService;
import com.fundaro.zodiac.taurus.service.notification.NotificationPreferenceMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

@Component
@ConditionalOnProperty(prefix = "application.notification-push-delivery", name = "scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class NotificationPushScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationPushScheduler.class);
    private final TenantSchemaRegistry tenantSchemaRegistry;
    private final TenantTransactionExecutor transactionExecutor;
    private final NotificationPushDeliveryService deliveryService;
    private final PushReminderDeliveryService reminderDeliveryService;
    private final NotificationPreferenceMetrics metrics;

    public NotificationPushScheduler(
        TenantSchemaRegistry tenantSchemaRegistry,
        TenantTransactionExecutor transactionExecutor,
        NotificationPushDeliveryService deliveryService,
        PushReminderDeliveryService reminderDeliveryService,
        NotificationPreferenceMetrics metrics
    ) {
        this.tenantSchemaRegistry = tenantSchemaRegistry;
        this.transactionExecutor = transactionExecutor;
        this.deliveryService = deliveryService;
        this.reminderDeliveryService = reminderDeliveryService;
        this.metrics = metrics;
    }

    /**
     * L'unico orchestratore delle consegne push: per ogni tenant elabora le due code, i job delle
     * notifiche e i promemoria degli eventi, con lo stesso trattamento — un lotto di ID pronti e poi
     * una transazione con lock per ciascun job.
     */
    @Scheduled(fixedDelayString = "${application.notification-push-delivery.poll-delay:5000}")
    public void dispatch() {
        for (String tenantCode : tenantSchemaRegistry.findActiveTenantCodes()) {
            drain(tenantCode, "push", deliveryService::findReadyIds, deliveryService::process);
            drain(tenantCode, "reminder", reminderDeliveryService::findReadyIds, reminderDeliveryService::process);
        }
    }

    private void drain(String tenantCode, String queue, Supplier<List<Long>> readyIds, LongConsumer job) {
        long startedAt = System.nanoTime();
        int processed = 0;
        for (Long id : transactionExecutor.execute(tenantCode, readyIds::get)) {
            processed++;
            try {
                transactionExecutor.execute(tenantCode, () -> job.accept(id));
            } catch (RuntimeException exception) {
                LOG.warn("Notification push job failed tenant={} queue={} jobId={} errorClass={}", tenantCode, queue, id, exception.getClass().getSimpleName());
            }
        }
        if (processed > 0) metrics.recordBatch(queue, processed, System.nanoTime() - startedAt);
    }
}
