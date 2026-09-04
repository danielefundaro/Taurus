package com.fundaro.zodiac.taurus.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaRegistry;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class FinanceRolloverSchedulerTest {

    @Test
    void shouldScheduleOnlyFinanceEnabledTenants() {
        FinanceService financeService = mock(FinanceService.class);
        TenantSchemaRegistry tenantSchemaRegistry = mock(TenantSchemaRegistry.class);
        TenantTransactionExecutor tenantTransactionExecutor = mock(TenantTransactionExecutor.class);
        when(tenantSchemaRegistry.findFinanceEnabledTenantCodes()).thenReturn(List.of("finance-on"));
        FinanceRolloverScheduler scheduler = new FinanceRolloverScheduler(
            financeService,
            tenantSchemaRegistry,
            tenantTransactionExecutor
        );

        scheduler.updateAnnualOpenings();

        verify(tenantTransactionExecutor).execute(eq("finance-on"), any(Supplier.class));
        verify(tenantSchemaRegistry).findFinanceEnabledTenantCodes();
        verify(tenantSchemaRegistry, never()).findActiveTenantCodes();
    }
}
