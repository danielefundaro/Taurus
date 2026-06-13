package com.fundaro.zodiac.taurus.service;

import com.fundaro.zodiac.taurus.domain.Tenants;
import com.fundaro.zodiac.taurus.domain.criteria.TenantsCriteria;
import com.fundaro.zodiac.taurus.service.dto.TenantsDTO;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import reactor.core.publisher.Mono;

public interface TenantsService extends CommonOpenSearchService<Tenants, TenantsDTO, TenantsCriteria> {
    Mono<TenantsDTO> findByCode(String code, AbstractAuthenticationToken abstractAuthenticationToken);
}
