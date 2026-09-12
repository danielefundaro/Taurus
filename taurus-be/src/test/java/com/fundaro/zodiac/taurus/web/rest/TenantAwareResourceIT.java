package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.domain.Tenants;
import com.fundaro.zodiac.taurus.multitenancy.TenantContext;
import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaProvisioningService;
import com.fundaro.zodiac.taurus.repository.TenantsRepository;
import java.util.Date;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Base per gli integration test delle risorse le cui tabelle vivono solo negli schemi tenant.
 *
 * <p>Serve a colmare due distanze fra il test e la richiesta reale. La prima è lo schema: la
 * tabella esiste solo dopo il provisioning, e le chiamate diritte al repository fatte dal corpo del
 * test non passano da {@code TenantContextInterceptor}, quindi il tenant va aperto a mano sul
 * thread di test. La seconda è il registro: i servizi interrogano
 * {@code TenantFeatureService}, che pretende una riga {@code tenant} attiva e non si accontenta
 * dello schema.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class TenantAwareResourceIT {

    /** Deve combaciare con il claim {@code tenant} di {@code @WithMockTenantUser}. */
    protected static final String TENANT_CODE = "resource-it-tenant";

    /** Deve combaciare con il claim {@code sub}: i servizi filtrano le righe per proprietario. */
    protected static final String TENANT_USER_ID = "AAAAAAAAAA";

    @Autowired
    private TenantSchemaProvisioningService tenantProvisioningService;

    @Autowired
    private TenantsRepository tenantsRegistry;

    private TenantContext.Scope tenantScope;

    @BeforeAll
    void provisionTenant() {
        registerTenant();
        tenantProvisioningService.provision(TENANT_CODE);
    }

    @AfterAll
    void dropTenant() {
        try {
            tenantProvisioningService.dropSchema(TENANT_CODE);
        } finally {
            unregisterTenant();
        }
    }

    @BeforeEach
    void openTenantScope() {
        tenantScope = TenantContext.use(TENANT_CODE);
    }

    @AfterEach
    void closeTenantScope() {
        if (tenantScope != null) {
            tenantScope.close();
            tenantScope = null;
        }
    }

    private void registerTenant() {
        unregisterTenant();
        Date now = new Date();
        Tenants tenant = new Tenants();
        tenant.setName(TENANT_CODE);
        tenant.setCode(TENANT_CODE);
        tenant.setActive(true);
        tenant.setFinanceEnabled(true);
        tenant.setInventoryEnabled(true);
        tenant.setNotificationPreferencesEnabled(true);
        tenant.setDeleted(false);
        tenant.setInsertBy(TENANT_USER_ID);
        tenant.setInsertDate(now);
        tenant.setEditBy(TENANT_USER_ID);
        tenant.setEditDate(now);
        tenant.setEntityVersion(0L);
        tenantsRegistry.save(tenant);
    }

    private void unregisterTenant() {
        tenantsRegistry.findByCodeAndDeletedFalse(TENANT_CODE).ifPresent(tenantsRegistry::delete);
    }
}
