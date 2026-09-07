package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.service.TenantFeatureService;
import com.fundaro.zodiac.taurus.service.dto.TenantFeaturesDTO;
import com.fundaro.zodiac.taurus.service.dto.TenantFeatureCapabilityDTO;
import com.fundaro.zodiac.taurus.domain.enumeration.TenantFeature;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenant-features")
public class TenantFeaturesResource {

    private final TenantFeatureService tenantFeatureService;

    public TenantFeaturesResource(TenantFeatureService tenantFeatureService) {
        this.tenantFeatureService = tenantFeatureService;
    }

    @GetMapping("/current")
    public TenantFeaturesDTO current() {
        return tenantFeatureService.current();
    }

    @GetMapping("/capabilities")
    @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
    public Map<TenantFeature, TenantFeatureCapabilityDTO> capabilities() {
        return tenantFeatureService.capabilities();
    }
}
