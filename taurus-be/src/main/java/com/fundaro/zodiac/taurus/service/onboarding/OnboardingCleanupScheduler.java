package com.fundaro.zodiac.taurus.service.onboarding;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaRegistry;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;

import java.time.ZonedDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Applies every night the retention of the imports on every active tenant: turning off the import does not suspend it.
 */
@Component
public class OnboardingCleanupScheduler {
    private static final Logger LOG = LoggerFactory.getLogger(OnboardingCleanupScheduler.class);
    public static final String SYSTEM_ACTOR = "onboarding-retention";

    private final OnboardingRetentionService retention;
    private final TenantSchemaRegistry tenantSchemaRegistry;
    private final TenantTransactionExecutor tenantTransactionExecutor;
    private final ApplicationProperties.OnboardingProperties properties;
    private final OnboardingMetrics metrics;

    public OnboardingCleanupScheduler(
        OnboardingRetentionService retention,
        TenantSchemaRegistry tenantSchemaRegistry,
        TenantTransactionExecutor tenantTransactionExecutor,
        ApplicationProperties applicationProperties,
        OnboardingMetrics metrics
    ) {
        this.retention = retention;
        this.tenantSchemaRegistry = tenantSchemaRegistry;
        this.tenantTransactionExecutor = tenantTransactionExecutor;
        this.properties = applicationProperties.getOnboarding();
        this.metrics = metrics;
    }

    @Scheduled(cron = "${application.onboarding.cleanup-cron:0 30 4 * * *}", zone = "Europe/Rome")
    public void removeExpiredData() {
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime abandonedBefore = now.minusDays(properties.getAbandonedRetentionDays());
        ZonedDateTime sourcesBefore = now.minusDays(properties.getSourceRetentionDays());
        ZonedDateTime recordsBefore = now.minusDays(properties.getAuditRetentionDays());
        tenantSchemaRegistry.findActiveTenantCodes().forEach(tenantCode -> {
            try {
                // Cancelled first: an abandoned import releases its file in this same run, not thirty days later.
                int abandoned = tenantTransactionExecutor.execute(
                    tenantCode,
                    () -> retention.cancelAbandoned(abandonedBefore, tenantCode, SYSTEM_ACTOR)
                );
                int sources = tenantTransactionExecutor.execute(
                    tenantCode,
                    () -> retention.removeSources(sourcesBefore, tenantCode, SYSTEM_ACTOR)
                );
                int records = tenantTransactionExecutor.execute(tenantCode, () -> retention.removeRecords(recordsBefore));
                metrics.retention("abandoned", abandoned);
                metrics.retention("sources", sources);
                metrics.retention("records", records);
                if (abandoned > 0 || sources > 0 || records > 0) {
                    LOG.info("onboarding_retention tenant={} abandoned={} sources={} records={}", tenantCode, abandoned, sources, records);
                }
            } catch (RuntimeException exception) {
                LOG.error("Unable to apply the retention of the imports for tenant {}", tenantCode, exception);
            }
        });
    }
}
