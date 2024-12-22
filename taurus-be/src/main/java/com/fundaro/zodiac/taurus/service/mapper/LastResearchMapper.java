package com.fundaro.zodiac.taurus.service.mapper;

import com.fundaro.zodiac.taurus.domain.LastResearch;
import com.fundaro.zodiac.taurus.service.dto.LastResearchDTO;
import org.mapstruct.Mapper;

/**
 * Mapper for the entity {@link LastResearch} and its DTO {@link LastResearchDTO}.
 */
@Mapper(componentModel = "spring")
public interface LastResearchMapper extends EntityMapper<LastResearchDTO, LastResearch> {}
