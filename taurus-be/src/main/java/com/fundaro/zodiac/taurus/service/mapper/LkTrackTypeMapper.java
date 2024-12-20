package com.fundaro.zodiac.taurus.service.mapper;

import com.fundaro.zodiac.taurus.domain.LkTrackType;
import com.fundaro.zodiac.taurus.service.dto.LkTrackTypeDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link LkTrackType} and its DTO {@link LkTrackTypeDTO}.
 */
@Mapper(componentModel = "spring")
public interface LkTrackTypeMapper extends EntityMapper<LkTrackTypeDTO, LkTrackType> {}
