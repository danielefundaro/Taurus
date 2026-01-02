package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.domain.Tenants;
import com.fundaro.zodiac.taurus.domain.criteria.TenantsCriteria;
import com.fundaro.zodiac.taurus.service.TenantsService;
import com.fundaro.zodiac.taurus.service.dto.TenantsDTO;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for managing {@link Tenants}.
 */
@RestController
@RequestMapping("/api/tenants")
public class TenantsResource extends CommonOpenSearchResource<Tenants, TenantsDTO, TenantsCriteria, TenantsService> {

    public TenantsResource(TenantsService tenantsService) {
        super(tenantsService, Tenants.class.getSimpleName(), TenantsResource.class);
    }
}
