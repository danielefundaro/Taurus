package com.fundaro.zodiac.taurus.service;

import com.fundaro.zodiac.taurus.domain.Tenants;
import com.fundaro.zodiac.taurus.domain.criteria.TenantsCriteria;
import com.fundaro.zodiac.taurus.service.dto.TenantsDTO;
import org.springframework.security.authentication.AbstractAuthenticationToken;

import java.util.Optional;

public interface TenantsService extends CommonOpenSearchService<Tenants, TenantsDTO, TenantsCriteria> {
    Optional<TenantsDTO> findByCode(String code, AbstractAuthenticationToken abstractAuthenticationToken);

    void deleteForGdpr(String id, AbstractAuthenticationToken abstractAuthenticationToken);
}
