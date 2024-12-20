package com.fundaro.zodiac.taurus.service.mapper;

import com.fundaro.zodiac.taurus.domain.Media;
import com.fundaro.zodiac.taurus.domain.Pieces;
import com.fundaro.zodiac.taurus.service.dto.MediaDTO;
import com.fundaro.zodiac.taurus.service.dto.PiecesDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link Pieces} and its DTO {@link PiecesDTO}.
 */
@Mapper(componentModel = "spring")
public interface PiecesMapper extends EntityMapper<PiecesDTO, Pieces> {
    @Mapping(target = "media", source = "media", qualifiedByName = "mediaId")
    PiecesDTO toDto(Pieces s);

    @Named("mediaId")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    MediaDTO toDtoMediaId(Media media);
}
