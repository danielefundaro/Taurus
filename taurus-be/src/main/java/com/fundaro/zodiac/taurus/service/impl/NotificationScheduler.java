package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaRegistry;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import java.time.ZonedDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(NotificationScheduler.class);
    private final TenantSchemaRegistry tenantSchemaRegistry;
    private final TenantTransactionExecutor tenantTransactionExecutor;
    private final NotificationDispatcher dispatcher;
    private final ApplicationProperties.NotificationProperties properties;
    private final NotificationMetrics metrics;

    public NotificationScheduler(
        TenantSchemaRegistry tenantSchemaRegistry,
        TenantTransactionExecutor tenantTransactionExecutor,
        NotificationDispatcher dispatcher,
        ApplicationProperties applicationProperties,
        NotificationMetrics metrics
    ) {
        this.tenantSchemaRegistry = tenantSchemaRegistry;
        this.tenantTransactionExecutor = tenantTransactionExecutor;
        this.dispatcher = dispatcher;
        this.properties = applicationProperties.getNotifications();
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${application.notifications.dispatch-delay:5000}")
    public void dispatchPendingNotifications() {
        tenantSchemaRegistry.findActiveTenantCodes().forEach(this::dispatchTenant);
    }

    @Scheduled(cron = "${application.notifications.cleanup-cron:0 30 3 * * *}")
    public void deleteDeliveredOutboxEvents() {
        ZonedDateTime cutoff = ZonedDateTime.now().minusDays(Math.max(1, properties.getOutboxRetentionDays()));
        tenantSchemaRegistry.findActiveTenantCodes().forEach(tenantCode -> {
            try {
                tenantTransactionExecutor.execute(tenantCode, () -> dispatcher.deleteDeliveredBefore(cutoff));
            } catch (RuntimeException exception) {
                LOG.warn("Unable to clean delivered notifications for tenant {}", tenantCode, exception);
            }
        });
    }

    private void dispatchTenant(String tenantCode) {
        long startedAt = System.nanoTime();
        try {
            dispatchTenantEvents(tenantCode);
        } finally {
            metrics.recordSchedulerDuration(tenantCode, System.nanoTime() - startedAt);
        }
    }

    private void dispatchTenantEvents(String tenantCode) {
        List<Long> ids;
        try {
            ids = tenantTransactionExecutor.execute(tenantCode, dispatcher::findReadyIds);
        } catch (RuntimeException exception) {
            LOG.warn("notification_batch_load_failed tenant={} errorClass={}", tenantCode, exception.getClass().getName());
            return;
        }
        ids.forEach(id -> dispatchOne(tenantCode, id));
    }

    private void dispatchOne(String tenantCode, long id) {
        try {
            tenantTransactionExecutor.execute(tenantCode, () -> dispatcher.dispatch(id));
        } catch (RuntimeException exception) {
            LOG.warn("notification_dispatch_failed tenant={} eventId={} errorClass={}", tenantCode, id, exception.getClass().getName());
            try {
                tenantTransactionExecutor.execute(tenantCode, () -> dispatcher.markFailure(id, exception));
            } catch (RuntimeException persistenceException) {
                LOG.error(
                    "notification_failure_persistence_failed tenant={} eventId={} errorClass={}",
                    tenantCode,
                    id,
                    persistenceException.getClass().getName()
                );
            }
        }
    }
}
