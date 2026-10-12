package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.multitenancy.TenantContext;
import com.fundaro.zodiac.taurus.multitenancy.TenantProvisioningStatus;
import com.fundaro.zodiac.taurus.service.dto.TenantStatusDTO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenant-status")
@TenantSchemaNotRequired
public class TenantStatusResource {

    private final TenantProvisioningStatus provisioningStatus;

    public TenantStatusResource(TenantProvisioningStatus provisioningStatus) {
        this.provisioningStatus = provisioningStatus;
    }

    /**
     * {@code GET /api/tenant-status} : whether the tenant of the session has an active schema. When it has none,
     * the client runs in bootstrap mode and only offers tenant management.
     */
    @GetMapping
    public TenantStatusDTO current() {
        String tenantCode = TenantContext.getTenantCode().orElse(null);
        return new TenantStatusDTO(tenantCode, provisioningStatus.isProvisioned(tenantCode));
    }
}
