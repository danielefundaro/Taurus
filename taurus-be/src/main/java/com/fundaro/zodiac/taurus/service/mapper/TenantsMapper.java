package com.fundaro.zodiac.taurus.service.mapper;

import com.fundaro.zodiac.taurus.domain.Tenants;
import com.fundaro.zodiac.taurus.service.dto.TenantsDTO;
import org.mapstruct.Mapper;

/**
 * Mapper for the entity {@link Tenants} and its DTO {@link TenantsDTO}.
 */
@Mapper(componentModel = "spring")
public interface TenantsMapper extends EntityOpenSearchMapper<TenantsDTO, Tenants> {
}
