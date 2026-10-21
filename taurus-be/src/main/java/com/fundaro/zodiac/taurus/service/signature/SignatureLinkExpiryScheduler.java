package com.fundaro.zodiac.taurus.service.signature;

import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaRegistry;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;

import java.time.ZonedDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Notifies the administrators, once, of the links expired without the signature of the parent, and removes from the
 * public registry the rows of the links that can no longer be used.
 */
@Component
public class SignatureLinkExpiryScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(SignatureLinkExpiryScheduler.class);
    public static final String SYSTEM_ACTOR = "signature-link-expiry";

    private final LoanSheetSignatureService signatureService;
    private final SignatureLinkService linkService;
    private final TenantSchemaRegistry tenantSchemaRegistry;
    private final TenantTransactionExecutor tenantTransactionExecutor;

    public SignatureLinkExpiryScheduler(
        LoanSheetSignatureService signatureService,
        SignatureLinkService linkService,
        TenantSchemaRegistry tenantSchemaRegistry,
        TenantTransactionExecutor tenantTransactionExecutor
    ) {
        this.signatureService = signatureService;
        this.linkService = linkService;
        this.tenantSchemaRegistry = tenantSchemaRegistry;
        this.tenantTransactionExecutor = tenantTransactionExecutor;
    }

    @Scheduled(
        cron = "${application.signature.link-expiry-cron:0 20 * * * *}",
        zone = "${application.inventory.expiration-notification-zone:Europe/Rome}"
    )
    public void notifyExpiredLinks() {
        ZonedDateTime now = ZonedDateTime.now();
        tenantSchemaRegistry.findInventoryEnabledTenantCodes().forEach(tenantCode -> {
            try {
                List<LoanSheetSignatureService.ExpiredLink> expired = tenantTransactionExecutor.execute(
                    tenantCode,
                    () -> signatureService.expiredLinks(now, SYSTEM_ACTOR)
                );
                if (!expired.isEmpty()) LOG.info("signature_links_expired tenant={} links={}", tenantCode, expired.size());
            } catch (RuntimeException exception) {
                LOG.error("Unable to notify the expired signature links of tenant {}", tenantCode, exception);
            }
        });
        try {
            int removed = linkService.removeInactiveRoutes(now);
            if (removed > 0) LOG.info("signature_link_routes_removed routes={}", removed);
        } catch (RuntimeException exception) {
            LOG.error("Unable to remove the inactive signature link routes", exception);
        }
    }
}
