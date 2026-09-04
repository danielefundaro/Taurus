package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.service.TenantFeatureService;
import com.fundaro.zodiac.taurus.service.dto.TenantFeaturesDTO;
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
}
