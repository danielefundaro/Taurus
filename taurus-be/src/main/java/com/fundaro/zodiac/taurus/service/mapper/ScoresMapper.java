package com.fundaro.zodiac.taurus.service.mapper;

import com.fundaro.zodiac.taurus.domain.Scores;
import com.fundaro.zodiac.taurus.service.dto.ScoresDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper for the entity {@link Scores} and its DTO {@link ScoresDTO}.
 */
@Mapper(componentModel = "spring")
public interface ScoresMapper extends EntityOpenSearchMapper<ScoresDTO, Scores> {
    @Mapping(target = "media", source = "media", qualifiedByName = "orderChildrenToDto")
    ScoresDTO toDto(Scores s);
}
