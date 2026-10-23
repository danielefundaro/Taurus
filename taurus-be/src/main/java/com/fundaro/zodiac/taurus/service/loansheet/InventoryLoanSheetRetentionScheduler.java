package com.fundaro.zodiac.taurus.service.loansheet;

import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaRegistry;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;

import java.time.ZonedDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Settles the loan sheets closed without charges once the grace period after closure has elapsed, removing their
 * personal data and PDFs as promised by the default privacy notice.
 */
@Component
public class InventoryLoanSheetRetentionScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(InventoryLoanSheetRetentionScheduler.class);
    public static final String SYSTEM_ACTOR = "inventory-loan-sheet-retention";

    private final InventoryLoanSheetService loanSheetService;
    private final TenantSchemaRegistry tenantSchemaRegistry;
    private final TenantTransactionExecutor tenantTransactionExecutor;
    private final int autoSettleDays;

    public InventoryLoanSheetRetentionScheduler(
        InventoryLoanSheetService loanSheetService,
        TenantSchemaRegistry tenantSchemaRegistry,
        TenantTransactionExecutor tenantTransactionExecutor,
        @Value("${application.inventory.loan-sheet.auto-settle-days:30}") int autoSettleDays
    ) {
        this.loanSheetService = loanSheetService;
        this.tenantSchemaRegistry = tenantSchemaRegistry;
        this.tenantTransactionExecutor = tenantTransactionExecutor;
        this.autoSettleDays = autoSettleDays;
    }

    @Scheduled(
        cron = "${application.inventory.loan-sheet.settle-cron:0 15 3 * * *}",
        zone = "${application.inventory.expiration-notification-zone:Europe/Rome}"
    )
    public void settleClosedSheets() {
        ZonedDateTime closedBefore = ZonedDateTime.now().minusDays(autoSettleDays);
        // Every active tenant: turning off the inventory or its loan sheets and signature does not suspend the retention.
        tenantSchemaRegistry.findActiveTenantCodes().forEach(tenantCode -> {
            try {
                int settled = tenantTransactionExecutor.execute(
                    tenantCode,
                    () -> loanSheetService.settleClosedWithoutCharges(closedBefore, tenantCode, SYSTEM_ACTOR)
                );
                if (settled > 0) LOG.info("inventory_loan_sheet_auto_settled tenant={} sheets={}", tenantCode, settled);
            } catch (RuntimeException exception) {
                LOG.error("Unable to settle closed inventory loan sheets for tenant {}", tenantCode, exception);
            }
        });
    }
}
