package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaRegistry;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import java.time.ZonedDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FinanceNotificationScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(FinanceNotificationScheduler.class);
    private final TenantSchemaRegistry tenantSchemaRegistry;
    private final TenantTransactionExecutor tenantTransactionExecutor;
    private final FinanceNotificationDispatcher dispatcher;

    public FinanceNotificationScheduler(
        TenantSchemaRegistry tenantSchemaRegistry,
        TenantTransactionExecutor tenantTransactionExecutor,
        FinanceNotificationDispatcher dispatcher
    ) {
        this.tenantSchemaRegistry = tenantSchemaRegistry;
        this.tenantTransactionExecutor = tenantTransactionExecutor;
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelayString = "${application.finance.notification-dispatch-delay:5000}")
    public void dispatchPendingNotifications() {
        tenantSchemaRegistry.findActiveTenantCodes().forEach(this::dispatchTenant);
    }

    @Scheduled(cron = "${application.finance.notification-cleanup-cron:0 30 3 * * *}")
    public void deleteDeliveredOutboxEvents() {
        ZonedDateTime cutoff = ZonedDateTime.now().minusDays(30);
        tenantSchemaRegistry.findActiveTenantCodes().forEach(tenantCode -> {
            try {
                tenantTransactionExecutor.execute(tenantCode, () -> dispatcher.deleteDeliveredBefore(cutoff));
            } catch (RuntimeException exception) {
                LOG.warn("Unable to clean delivered finance notifications for tenant {}", tenantCode, exception);
            }
        });
    }

    private void dispatchTenant(String tenantCode) {
        List<Long> ids;
        try {
            ids = tenantTransactionExecutor.execute(tenantCode, dispatcher::findReadyIds);
        } catch (RuntimeException exception) {
            LOG.warn("Unable to load pending finance notifications for tenant {}", tenantCode, exception);
            return;
        }
        ids.forEach(id -> dispatchOne(tenantCode, id));
    }

    private void dispatchOne(String tenantCode, long id) {
        try {
            tenantTransactionExecutor.execute(tenantCode, () -> dispatcher.dispatch(id));
        } catch (RuntimeException exception) {
            LOG.warn("Unable to deliver finance notification {} for tenant {}; it will be retried", id, tenantCode, exception);
            try {
                tenantTransactionExecutor.execute(tenantCode, () -> dispatcher.markFailure(id, exception));
            } catch (RuntimeException persistenceException) {
                LOG.error("Unable to persist finance notification failure {} for tenant {}", id, tenantCode, persistenceException);
            }
        }
    }
}
