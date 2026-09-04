package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaRegistry;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import java.time.LocalDate;
import java.time.ZoneId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class FinanceRolloverScheduler {

    public static final String SYSTEM_ACTOR = "finance-rollover-scheduler";
    private static final Logger LOG = LoggerFactory.getLogger(FinanceRolloverScheduler.class);
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Europe/Rome");

    private final FinanceService financeService;
    private final TenantSchemaRegistry tenantSchemaRegistry;
    private final TenantTransactionExecutor tenantTransactionExecutor;

    public FinanceRolloverScheduler(
        FinanceService financeService,
        TenantSchemaRegistry tenantSchemaRegistry,
        TenantTransactionExecutor tenantTransactionExecutor
    ) {
        this.financeService = financeService;
        this.tenantSchemaRegistry = tenantSchemaRegistry;
        this.tenantTransactionExecutor = tenantTransactionExecutor;
    }

    @Scheduled(
        cron = "${application.finance.rollover-cron:0 10 0 * * *}",
        zone = "${application.finance.rollover-zone:Europe/Rome}"
    )
    public void updateAnnualOpenings() {
        int previousYear = LocalDate.now(DEFAULT_ZONE).getYear() - 1;
        tenantSchemaRegistry.findFinanceEnabledTenantCodes().forEach(tenantCode -> {
            try {
                tenantTransactionExecutor.execute(tenantCode, () -> financeService.rolloverForActor(previousYear, SYSTEM_ACTOR));
            } catch (RuntimeException exception) {
                LOG.error("Unable to update annual finance openings for tenant {}", tenantCode, exception);
            }
        });
    }
}
