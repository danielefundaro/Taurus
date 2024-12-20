package com.fundaro.zodiac.taurus.service.mapper;

import com.fundaro.zodiac.taurus.domain.LkTrackType;
import com.fundaro.zodiac.taurus.domain.Tracks;
import com.fundaro.zodiac.taurus.service.dto.LkTrackTypeDTO;
import com.fundaro.zodiac.taurus.service.dto.TracksDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link Tracks} and its DTO {@link TracksDTO}.
 */
@Mapper(componentModel = "spring")
public interface TracksMapper extends EntityMapper<TracksDTO, Tracks> {
    @Mapping(target = "type", source = "type", qualifiedByName = "lkTrackTypeId")
    TracksDTO toDto(Tracks s);

    @Named("lkTrackTypeId")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    LkTrackTypeDTO toDtoLkTrackTypeId(LkTrackType lkTrackType);
}
