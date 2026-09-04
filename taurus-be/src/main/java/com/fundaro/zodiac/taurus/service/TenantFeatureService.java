package com.fundaro.zodiac.taurus.service;

import com.fundaro.zodiac.taurus.domain.Tenants;
import com.fundaro.zodiac.taurus.domain.enumeration.TenantFeature;
import com.fundaro.zodiac.taurus.multitenancy.TenantContext;
import com.fundaro.zodiac.taurus.repository.TenantsRepository;
import com.fundaro.zodiac.taurus.service.dto.TenantFeaturesDTO;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TenantFeatureService {

    private final TenantsRepository tenantsRepository;

    public TenantFeatureService(TenantsRepository tenantsRepository) {
        this.tenantsRepository = tenantsRepository;
    }

    public TenantFeaturesDTO current() {
        Tenants tenant = currentTenant();
        return new TenantFeaturesDTO(
            tenant.getCode(),
            tenant.getEntityVersion(),
            Boolean.TRUE.equals(tenant.getFinanceEnabled()),
            Boolean.TRUE.equals(tenant.getInventoryEnabled())
        );
    }

    public boolean isEnabled(TenantFeature feature) {
        Tenants tenant = currentTenant();
        return switch (feature) {
            case FINANCE -> Boolean.TRUE.equals(tenant.getFinanceEnabled());
            case INVENTORY -> Boolean.TRUE.equals(tenant.getInventoryEnabled());
        };
    }

    public void requireEnabled(TenantFeature feature) {
        if (!isEnabled(feature)) {
            String featureName = feature.name().toLowerCase(java.util.Locale.ROOT);
            throw new RequestAlertException(
                HttpStatus.FORBIDDEN,
                "Tenant feature is disabled",
                "TenantFeature",
                "tenantFeature." + featureName + ".disabled"
            );
        }
    }

    private Tenants currentTenant() {
        String tenantCode = TenantContext.getTenantCode().orElseThrow(() -> invalidTenant("Tenant context is required"));
        return tenantsRepository.findByCodeAndDeletedFalse(tenantCode)
            .filter(tenant -> Boolean.TRUE.equals(tenant.getActive()))
            .orElseThrow(() -> invalidTenant("Tenant is missing or inactive"));
    }

    private RequestAlertException invalidTenant(String message) {
        return new RequestAlertException(HttpStatus.FORBIDDEN, message, "TenantFeature", "tenantFeature.tenant.invalid");
    }
}
