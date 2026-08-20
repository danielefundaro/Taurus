package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaRegistry;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "application.retention", name = "cleanup-enabled", havingValue = "true", matchIfMissing = true)
public class DataRetentionScheduler {

    private final DataErasureService dataErasureService;
    private final TenantSchemaRegistry tenantSchemaRegistry;
    private final TenantTransactionExecutor tenantTransactionExecutor;

    public DataRetentionScheduler(
        DataErasureService dataErasureService,
        TenantSchemaRegistry tenantSchemaRegistry,
        TenantTransactionExecutor tenantTransactionExecutor
    ) {
        this.dataErasureService = dataErasureService;
        this.tenantSchemaRegistry = tenantSchemaRegistry;
        this.tenantTransactionExecutor = tenantTransactionExecutor;
    }

    @Scheduled(cron = "${application.retention.cleanup-cron:0 0 3 * * *}")
    public void cleanupExpiredData() {
        tenantSchemaRegistry.findActiveTenantCodes().forEach(tenantCode ->
            tenantTransactionExecutor.execute(tenantCode, dataErasureService::purgeExpiredData)
        );
    }
}
