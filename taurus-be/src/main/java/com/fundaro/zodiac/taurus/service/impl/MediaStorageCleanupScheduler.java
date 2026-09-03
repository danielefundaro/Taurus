package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaRegistry;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Pulizia periodica dello storage: file temporanei e file orfani di ogni tenant
 * attivo, ciascuno nel proprio schema e nella propria directory.
 */
@Component
@ConditionalOnProperty(prefix = "application.media", name = "cleanup-enabled", havingValue = "true", matchIfMissing = true)
public class MediaStorageCleanupScheduler {

    private static final Logger log = LoggerFactory.getLogger(MediaStorageCleanupScheduler.class);

    private final MediaStorageCleanupService mediaStorageCleanupService;
    private final TenantSchemaRegistry tenantSchemaRegistry;
    private final TenantTransactionExecutor tenantTransactionExecutor;

    public MediaStorageCleanupScheduler(
        MediaStorageCleanupService mediaStorageCleanupService,
        TenantSchemaRegistry tenantSchemaRegistry,
        TenantTransactionExecutor tenantTransactionExecutor
    ) {
        this.mediaStorageCleanupService = mediaStorageCleanupService;
        this.tenantSchemaRegistry = tenantSchemaRegistry;
        this.tenantTransactionExecutor = tenantTransactionExecutor;
    }

    @Scheduled(cron = "${application.media.cleanup-cron:0 30 3 * * *}")
    public void cleanupStorage() {
        tenantSchemaRegistry
            .findActiveTenantCodes()
            .forEach(tenantCode -> {
                try {
                    tenantTransactionExecutor.execute(tenantCode, () -> mediaStorageCleanupService.cleanupCurrentTenant(tenantCode));
                } catch (RuntimeException exception) {
                    log.warn("Media storage cleanup failed for tenant {}", tenantCode, exception);
                }
            });
    }
}
