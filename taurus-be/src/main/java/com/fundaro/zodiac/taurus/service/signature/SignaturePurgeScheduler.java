package com.fundaro.zodiac.taurus.service.signature;

import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaRegistry;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;

import java.time.ZonedDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Removes every night the documents, attestations and personal data of the signed acts whose grace period after the
 * settlement or the cancellation of the sheet has elapsed: the tenant keeps them in the dossier.
 */
@Component
public class SignaturePurgeScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(SignaturePurgeScheduler.class);
    public static final String SYSTEM_ACTOR = "signature-purge";

    private final LoanSheetSignatureService signatureService;
    private final TenantSchemaRegistry tenantSchemaRegistry;
    private final TenantTransactionExecutor tenantTransactionExecutor;

    public SignaturePurgeScheduler(
        LoanSheetSignatureService signatureService,
        TenantSchemaRegistry tenantSchemaRegistry,
        TenantTransactionExecutor tenantTransactionExecutor
    ) {
        this.signatureService = signatureService;
        this.tenantSchemaRegistry = tenantSchemaRegistry;
        this.tenantTransactionExecutor = tenantTransactionExecutor;
    }

    @Scheduled(
        cron = "${application.signature.purge-cron:0 50 3 * * *}",
        zone = "${application.inventory.expiration-notification-zone:Europe/Rome}"
    )
    public void purgeExpiredActs() {
        ZonedDateTime now = ZonedDateTime.now();
        tenantSchemaRegistry.findInventoryEnabledTenantCodes().forEach(tenantCode -> {
            try {
                int purged = tenantTransactionExecutor.execute(tenantCode, () -> signatureService.purgeDue(now, tenantCode, SYSTEM_ACTOR));
                if (purged > 0) LOG.info("signature_acts_purged tenant={} acts={}", tenantCode, purged);
            } catch (RuntimeException exception) {
                LOG.error("Unable to remove the expired signature acts of tenant {}", tenantCode, exception);
            }
        });
    }
}
