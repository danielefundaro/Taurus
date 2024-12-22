package com.fundaro.zodiac.taurus.service.mapper;

import com.fundaro.zodiac.taurus.domain.Instruments;
import com.fundaro.zodiac.taurus.domain.Media;
import com.fundaro.zodiac.taurus.domain.Performers;
import com.fundaro.zodiac.taurus.service.dto.InstrumentsDTO;
import com.fundaro.zodiac.taurus.service.dto.MediaDTO;
import com.fundaro.zodiac.taurus.service.dto.PerformersDTO;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

/**
 * Mapper for the entity {@link Performers} and its DTO {@link PerformersDTO}.
 */
@Mapper(componentModel = "spring")
public interface PerformersMapper extends EntityMapper<PerformersDTO, Performers> {
    @Mapping(target = "instrument", source = "instrument", qualifiedByName = "instrumentsId")
    @Mapping(target = "media", source = "media", qualifiedByName = "mediaId")
    PerformersDTO toDto(Performers s);

    @Named("instrumentsId")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    InstrumentsDTO toDtoInstrumentsId(Instruments instruments);

    @Named("mediaId")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    MediaDTO toDtoMediaId(Media media);
}
