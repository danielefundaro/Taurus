package com.fundaro.zodiac.taurus.multitenancy;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.boot.ApplicationArguments;

class TenantSchemaMigrationRunnerTest {

    private final TenantSchemaRegistry tenantSchemaRegistry = mock(TenantSchemaRegistry.class);
    private final TenantSchemaProvisioningService provisioningService = mock(TenantSchemaProvisioningService.class);
    private final TenantSchemaMigrationRunner runner = new TenantSchemaMigrationRunner(tenantSchemaRegistry, provisioningService);
    private final ApplicationArguments arguments = mock(ApplicationArguments.class);

    @Test
    void migratesEveryActiveTenantInRegistryOrder() {
        when(tenantSchemaRegistry.requireActiveTenantCodes()).thenReturn(List.of("alpha", "beta"));

        runner.run(arguments);

        InOrder migrations = inOrder(provisioningService);
        migrations.verify(provisioningService).migrateExistingSchema("alpha");
        migrations.verify(provisioningService).migrateExistingSchema("beta");
    }

    @Test
    void doesNothingWhenThereAreNoActiveTenants() {
        when(tenantSchemaRegistry.requireActiveTenantCodes()).thenReturn(List.of());

        runner.run(arguments);

        verify(provisioningService, never()).migrateExistingSchema(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void stopsStartupAtTheFirstFailedMigration() {
        TenantSchemaProvisioningException failure = new TenantSchemaProvisioningException("migration failed", new Exception("test"));
        when(tenantSchemaRegistry.requireActiveTenantCodes()).thenReturn(List.of("alpha", "beta"));
        org.mockito.Mockito.doThrow(failure).when(provisioningService).migrateExistingSchema("alpha");

        assertThatThrownBy(() -> runner.run(arguments)).isSameAs(failure);

        verify(provisioningService, never()).migrateExistingSchema("beta");
    }
}
