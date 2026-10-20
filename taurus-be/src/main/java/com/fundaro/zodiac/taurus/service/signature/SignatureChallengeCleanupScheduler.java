package com.fundaro.zodiac.taurus.service.signature;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.signature.SignatureChallengeStatus;
import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaRegistry;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import com.fundaro.zodiac.taurus.repository.signature.SignatureChallengeRepository;

import java.time.ZonedDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Removes the confirmation requests closed or expired for longer than the retention period: the evidence keeps only
 * their identifier.
 */
@Component
public class SignatureChallengeCleanupScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(SignatureChallengeCleanupScheduler.class);

    private final SignatureChallengeRepository challengeRepository;
    private final TenantSchemaRegistry tenantSchemaRegistry;
    private final TenantTransactionExecutor tenantTransactionExecutor;
    private final int retentionDays;

    public SignatureChallengeCleanupScheduler(
        SignatureChallengeRepository challengeRepository,
        TenantSchemaRegistry tenantSchemaRegistry,
        TenantTransactionExecutor tenantTransactionExecutor,
        ApplicationProperties applicationProperties
    ) {
        this.challengeRepository = challengeRepository;
        this.tenantSchemaRegistry = tenantSchemaRegistry;
        this.tenantTransactionExecutor = tenantTransactionExecutor;
        this.retentionDays = applicationProperties.getSignature().getChallengeRetentionDays();
    }

    @Scheduled(
        cron = "${application.signature.cleanup-cron:0 45 3 * * *}",
        zone = "${application.inventory.expiration-notification-zone:Europe/Rome}"
    )
    public void removeClosedChallenges() {
        ZonedDateTime before = ZonedDateTime.now().minusDays(retentionDays);
        tenantSchemaRegistry.findInventoryEnabledTenantCodes().forEach(tenantCode -> {
            try {
                int removed = tenantTransactionExecutor.execute(
                    tenantCode,
                    () -> challengeRepository.deleteClosedBefore(before, SignatureChallengeStatus.OPEN)
                );
                if (removed > 0) LOG.info("signature_challenges_removed tenant={} challenges={}", tenantCode, removed);
            } catch (RuntimeException exception) {
                LOG.error("Unable to remove the closed signature challenges of tenant {}", tenantCode, exception);
            }
        });
    }
}
