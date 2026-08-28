package com.fundaro.zodiac.taurus.multitenancy;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TenantSchemaMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TenantSchemaMigrationRunner.class);

    private final TenantSchemaRegistry tenantSchemaRegistry;
    private final TenantSchemaProvisioningService provisioningService;

    public TenantSchemaMigrationRunner(
        TenantSchemaRegistry tenantSchemaRegistry,
        TenantSchemaProvisioningService provisioningService
    ) {
        this.tenantSchemaRegistry = tenantSchemaRegistry;
        this.provisioningService = provisioningService;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> tenantCodes = tenantSchemaRegistry.requireActiveTenantCodes();
        if (tenantCodes.isEmpty()) {
            log.info("No active PostgreSQL tenant schemas to migrate");
            return;
        }

        log.info("Migrating {} active PostgreSQL tenant schema(s)", tenantCodes.size());
        tenantCodes.forEach(provisioningService::migrateExistingSchema);
        log.info("All active PostgreSQL tenant schemas are up to date");
    }
}
