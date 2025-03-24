package com.fundaro.zodiac.taurus.service.mapper;

import com.fundaro.zodiac.taurus.domain.SheetsMusic;
import com.fundaro.zodiac.taurus.domain.Tracks;
import com.fundaro.zodiac.taurus.service.dto.ChildrenEntitiesDTO;
import com.fundaro.zodiac.taurus.service.dto.SheetsMusicDTO;
import com.fundaro.zodiac.taurus.service.dto.TracksDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
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

    @Mapping(target = "instruments", source = "instruments", qualifiedByName = "orderChildrenToDto")
    SheetsMusicDTO toSheetsMusicDTO(SheetsMusic s);

    @Named("orderScores")
    default Set<SheetsMusicDTO> orderScores(Set<SheetsMusic> scores) {
        return scores.stream().map(this::toSheetsMusicDTO).sorted(Comparator.comparing(ChildrenEntitiesDTO::getOrder)).collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
