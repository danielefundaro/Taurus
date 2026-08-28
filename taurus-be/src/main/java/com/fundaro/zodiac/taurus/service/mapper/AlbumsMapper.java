package com.fundaro.zodiac.taurus.service.mapper;

import com.fundaro.zodiac.taurus.domain.Albums;
import com.fundaro.zodiac.taurus.service.dto.AlbumsDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.BeanMapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

import com.fundaro.zodiac.taurus.domain.Tracks;
import com.fundaro.zodiac.taurus.service.dto.ChildrenEntitiesDTO;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Mapper for the entity {@link Albums} and its DTO {@link AlbumsDTO}.
 */
@Mapper(componentModel = "spring")
public interface AlbumsMapper extends EntityOpenSearchMapper<AlbumsDTO, Albums> {
    @Mapping(target = "tracks", expression = "java(toTrackRefs(albums.getTracks()))")
    AlbumsDTO toDto(Albums albums);

    @Override
    @Mapping(target = "tracks", ignore = true)
    Albums toEntity(AlbumsDTO dto);

    @Override
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "tracks", ignore = true)
    void partialUpdate(@MappingTarget Albums entity, AlbumsDTO dto);

    default Set<ChildrenEntitiesDTO> toTrackRefs(List<Tracks> tracks) {
        if (tracks == null) return null;
        final long[] order = {0L};
        return tracks.stream().map(track -> {
            ChildrenEntitiesDTO ref = new ChildrenEntitiesDTO();
            ref.setIndex(track.getId());
            ref.setName(track.getName());
            ref.setOrder(++order[0]);
            return ref;
        }).collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
