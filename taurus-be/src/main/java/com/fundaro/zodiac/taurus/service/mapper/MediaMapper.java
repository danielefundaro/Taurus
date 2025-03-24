package com.fundaro.zodiac.taurus.service.mapper;

import com.fundaro.zodiac.taurus.domain.Media;
import com.fundaro.zodiac.taurus.service.dto.MediaDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper for the entity {@link Media} and its DTO {@link MediaDTO}.
 */
@Mapper(componentModel = "spring")
public interface MediaMapper extends EntityOpenSearchMapper<MediaDTO, Media> {
    @Mapping(target = "path", source = "path")
    @Mapping(target = "contentType", source = "contentType")
    MediaDTO toDto(Media s);
}
