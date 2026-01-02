package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.Tenants;
import com.fundaro.zodiac.taurus.domain.criteria.TenantsCriteria;
import com.fundaro.zodiac.taurus.service.OpenSearchService;
import com.fundaro.zodiac.taurus.service.TenantsService;
import com.fundaro.zodiac.taurus.service.dto.TenantsDTO;
import com.fundaro.zodiac.taurus.service.mapper.TenantsMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link Tenants}.
 */
@Service
@Transactional
public class TenantsServiceImpl extends CommonOpenSearchServiceImpl<Tenants, TenantsDTO, TenantsCriteria, TenantsMapper> implements TenantsService {
    public TenantsServiceImpl(OpenSearchService openSearchService, TenantsMapper mapper) {
        super(openSearchService, mapper, TenantsService.class, Tenants.class);
    }
}
