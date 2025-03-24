package com.fundaro.zodiac.taurus.service.mapper;

import com.fundaro.zodiac.taurus.domain.Albums;
import com.fundaro.zodiac.taurus.service.dto.AlbumsDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper for the entity {@link Albums} and its DTO {@link AlbumsDTO}.
 */
@Mapper(componentModel = "spring")
public interface AlbumsMapper extends EntityOpenSearchMapper<AlbumsDTO, Albums> {
    @Mapping(target = "tracks", source = "tracks", qualifiedByName = "orderChildrenToDto")
    AlbumsDTO toDto(Albums albums);
}
