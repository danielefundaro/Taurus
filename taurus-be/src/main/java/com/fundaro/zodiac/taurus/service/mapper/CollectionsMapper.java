package com.fundaro.zodiac.taurus.service.mapper;

import com.fundaro.zodiac.taurus.domain.Albums;
import com.fundaro.zodiac.taurus.domain.Collections;
import com.fundaro.zodiac.taurus.domain.Tracks;
import com.fundaro.zodiac.taurus.service.dto.AlbumsDTO;
import com.fundaro.zodiac.taurus.service.dto.CollectionsDTO;
import com.fundaro.zodiac.taurus.service.dto.TracksDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link Collections} and its DTO {@link CollectionsDTO}.
 */
@Mapper(componentModel = "spring")
public interface CollectionsMapper extends EntityMapper<CollectionsDTO, Collections> {
    @Mapping(target = "album", source = "album", qualifiedByName = "albumsId")
    @Mapping(target = "track", source = "track", qualifiedByName = "tracksId")
    CollectionsDTO toDto(Collections s);

    @Named("albumsId")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    AlbumsDTO toDtoAlbumsId(Albums albums);

    @Named("tracksId")
    @BeanMapping(ignoreByDefault = true)
    @Mapping(target = "id", source = "id")
    TracksDTO toDtoTracksId(Tracks tracks);
}
