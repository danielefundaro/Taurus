package com.fundaro.zodiac.taurus.service.mapper;

import com.fundaro.zodiac.taurus.domain.Users;
import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.service.dto.ChildrenEntitiesDTO;
import com.fundaro.zodiac.taurus.service.dto.UsersDTO;
import com.fundaro.zodiac.taurus.utils.keycloak.domain.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.MappingTarget;
import org.mapstruct.BeanMapping;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Mapper for the entity {@link Users} and its DTO {@link UsersDTO}.
 */
@Mapper(componentModel = "spring")
public interface UsersMapper extends EntityOpenSearchMapper<UsersDTO, Users> {
    @Mapping(target = "instruments", expression = "java(toInstrumentRefs(s.getInstruments()))")
    UsersDTO toDto(Users s);

    @Override
    @Mapping(target = "instruments", ignore = true)
    @Mapping(target = "tenants", ignore = true)
    @Mapping(target = "userIdentity", ignore = true)
    Users toEntity(UsersDTO dto);

    @Override
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "instruments", ignore = true)
    @Mapping(target = "tenants", ignore = true)
    @Mapping(target = "userIdentity", ignore = true)
    void partialUpdate(@MappingTarget Users entity, UsersDTO dto);

    default Set<ChildrenEntitiesDTO> toInstrumentRefs(List<com.fundaro.zodiac.taurus.domain.Instruments> instruments) {
        if (instruments == null) return null;
        final long[] order = {0L};
        return instruments.stream().map(instrument -> {
            ChildrenEntitiesDTO ref = new ChildrenEntitiesDTO();
            ref.setIndex(instrument.getId());
            ref.setName(instrument.getName());
            ref.setOrder(++order[0]);
            return ref;
        }).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    @Mapping(target = "id", source = "keycloakId")
    @Mapping(target = "firstName", source = "name")
    @Mapping(target = "lastName", source = "lastName")
    @Mapping(target = "email", source = "email")
    @Mapping(target = "username", source = "email")
    @Mapping(target = "enabled", source = "active")
    User toKeycloakUser(UsersDTO s);
}
