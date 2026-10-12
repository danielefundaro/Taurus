package com.fundaro.zodiac.taurus.web.rest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fundaro.zodiac.taurus.IntegrationTest;
import com.fundaro.zodiac.taurus.test.util.WithMockTenantUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Primo accesso: la sessione usa un tenant senza schema (come il gruppo di bootstrap {@code temp}) e deve poter
 * gestire i tenant senza che le API dello schema tenant falliscano su relazioni inesistenti.
 */
@IntegrationTest
@AutoConfigureMockMvc
class TenantBootstrapIT extends TenantAwareResourceIT {

    private static final String BOOTSTRAP_TENANT = "bootstrap-it-temp";

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockTenantUser(tenant = BOOTSTRAP_TENANT, authorities = "ROLE_SUPER_ADMIN")
    void reportsTheBootstrapTenantAsNotProvisioned() throws Exception {
        mockMvc
            .perform(get("/api/tenant-status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tenantCode").value(BOOTSTRAP_TENANT))
            .andExpect(jsonPath("$.provisioned").value(false));
    }

    @Test
    @WithMockTenantUser(tenant = BOOTSTRAP_TENANT, authorities = "ROLE_SUPER_ADMIN")
    void rejectsTenantSchemaRequestsWithAConflict() throws Exception {
        mockMvc
            .perform(get("/api/preferences"))
            .andExpect(status().isConflict())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.message").value("error.tenant.notProvisioned"));
    }

    @Test
    @WithMockTenantUser(tenant = BOOTSTRAP_TENANT, authorities = "ROLE_SUPER_ADMIN")
    void keepsTenantManagementAvailable() throws Exception {
        mockMvc.perform(get("/api/tenants")).andExpect(status().isOk());
        mockMvc.perform(get("/api/tenant-features/capabilities")).andExpect(status().isOk());
    }

    @Test
    @WithMockTenantUser
    void servesTenantSchemaRequestsOnceTheSchemaIsActive() throws Exception {
        mockMvc
            .perform(get("/api/tenant-status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tenantCode").value(TENANT_CODE))
            .andExpect(jsonPath("$.provisioned").value(true));
        mockMvc.perform(get("/api/preferences")).andExpect(status().isOk());
    }
}
