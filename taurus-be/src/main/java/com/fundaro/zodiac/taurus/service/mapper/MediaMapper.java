package com.fundaro.zodiac.taurus.service.mapper;

import com.fundaro.zodiac.taurus.domain.Instruments;
import com.fundaro.zodiac.taurus.domain.Media;
import com.fundaro.zodiac.taurus.domain.Tracks;
import com.fundaro.zodiac.taurus.service.dto.InstrumentsDTO;
import com.fundaro.zodiac.taurus.service.dto.MediaDTO;
import com.fundaro.zodiac.taurus.service.dto.TracksDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link Media} and its DTO {@link MediaDTO}.
 */
@Mapper(componentModel = "spring")
public interface MediaMapper extends EntityMapper<MediaDTO, Media> {
    @Mapping(target = "track", source = "track", qualifiedByName = "tracksId")
    MediaDTO toDto(Media s);

    @Named("tracksId")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    TracksDTO toDtoTracksId(Tracks tracks);
}
