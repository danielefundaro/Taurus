package com.fundaro.zodiac.taurus.service.mapper;

import com.fundaro.zodiac.taurus.domain.SheetsMusic;
import com.fundaro.zodiac.taurus.domain.Tracks;
import com.fundaro.zodiac.taurus.service.dto.SheetsMusicDTO;
import com.fundaro.zodiac.taurus.service.dto.TracksDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.BeanMapping;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.Named;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Mapper for the entity {@link Tracks} and its DTO {@link TracksDTO}.
 */
@Mapper(componentModel = "spring")
public interface TracksMapper extends EntityOpenSearchMapper<TracksDTO, Tracks> {
    @Mapping(target = "scores", source = "scores", qualifiedByName = "orderScores")
    TracksDTO toDto(Tracks s);

    @Override
    @Mapping(target = "scores", ignore = true)
    Tracks toEntity(TracksDTO dto);

    @Override
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "scores", ignore = true)
    void partialUpdate(@MappingTarget Tracks entity, TracksDTO dto);

    @Mapping(target = "instruments", expression = "java(toInstrumentRefs(s.getInstruments()))")
    @Mapping(target = "media", expression = "java(toMediaRefs(s.getMedia()))")
    SheetsMusicDTO toSheetsMusicDTO(SheetsMusic s);

    @Named("orderScores")
    default Set<SheetsMusicDTO> orderScores(java.util.List<SheetsMusic> scores) {
        if (scores != null) {
            final long[] order = {0L};
            return scores.stream().map(score -> {
                SheetsMusicDTO dto = toSheetsMusicDTO(score);
                dto.setOrder(++order[0]);
                return dto;
            }).collect(Collectors.toCollection(LinkedHashSet::new));
        }

        return null;
    }

    default Set<com.fundaro.zodiac.taurus.service.dto.ChildrenEntitiesDTO> toMediaRefs(java.util.List<com.fundaro.zodiac.taurus.domain.Media> media) {
        if (media == null) return null;
        final long[] order = {0L};
        return media.stream().map(item -> {
            com.fundaro.zodiac.taurus.service.dto.ChildrenEntitiesDTO ref = new com.fundaro.zodiac.taurus.service.dto.ChildrenEntitiesDTO();
            ref.setIndex(item.getId()); ref.setName(item.getName()); ref.setOrder(++order[0]); return ref;
        }).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    default Set<com.fundaro.zodiac.taurus.service.dto.ChildrenEntitiesDTO> toInstrumentRefs(java.util.List<com.fundaro.zodiac.taurus.domain.Instruments> instruments) {
        if (instruments == null) return null;
        final long[] order = {0L};
        return instruments.stream().map(item -> {
            com.fundaro.zodiac.taurus.service.dto.ChildrenEntitiesDTO ref = new com.fundaro.zodiac.taurus.service.dto.ChildrenEntitiesDTO();
            ref.setIndex(item.getId()); ref.setName(item.getName()); ref.setOrder(++order[0]); return ref;
        }).collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
