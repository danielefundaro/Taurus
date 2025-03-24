package com.fundaro.zodiac.taurus.service.mapper;

import com.fundaro.zodiac.taurus.domain.ChildrenEntities;
import com.fundaro.zodiac.taurus.domain.CommonFieldsOpenSearch;
import com.fundaro.zodiac.taurus.service.dto.ChildrenEntitiesDTO;
import com.fundaro.zodiac.taurus.service.dto.CommonFieldsOpenSearchDTO;
import org.mapstruct.BeanMapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Contract for a generic dto to entity mapper.
 *
 * @param <D> - DTO type parameter.
 * @param <E> - Entity type parameter.
 */

public interface EntityOpenSearchMapper<D extends CommonFieldsOpenSearchDTO, E extends CommonFieldsOpenSearch> {
    E toEntity(D dto);

    D toDto(E entity);

    List<E> toEntity(List<D> dtoList);

    List<D> toDto(List<E> entityList);

    @Named("partialUpdate")
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void partialUpdate(@MappingTarget E entity, D dto);

    ChildrenEntitiesDTO childrenToDto(ChildrenEntities dtoList);

    @Named("orderChildrenToDto")
    default Set<ChildrenEntitiesDTO> orderChildrenToDto(Set<ChildrenEntities> entitySet) {
        return entitySet.stream().map(this::childrenToDto)
            .sorted(Comparator.comparing(ChildrenEntitiesDTO::getOrder))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
