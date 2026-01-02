package com.fundaro.zodiac.taurus.service;

import com.fundaro.zodiac.taurus.domain.Tenants;
import com.fundaro.zodiac.taurus.domain.criteria.TenantsCriteria;
import com.fundaro.zodiac.taurus.service.dto.TenantsDTO;

public interface TenantsService extends CommonOpenSearchService<Tenants, TenantsDTO, TenantsCriteria> {
}
