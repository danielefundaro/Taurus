package com.fundaro.zodiac.taurus.service.mapper;

import com.fundaro.zodiac.taurus.domain.Users;
import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.service.dto.ChildrenEntitiesDTO;
import com.fundaro.zodiac.taurus.service.dto.UsersDTO;
import com.fundaro.zodiac.taurus.utils.keycloak.domain.User;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Mapper for the entity {@link Users} and its DTO {@link UsersDTO}.
 */
@Mapper(componentModel = "spring")
public interface UsersMapper extends EntityOpenSearchMapper<UsersDTO, Users> {
    @Mapping(target = "instruments", source = "instruments", qualifiedByName = "orderChildrenToDto")
    UsersDTO toDto(Users s);

    @Mapping(target = "id", source = "keycloakId")
    @Mapping(target = "firstName", source = "name")
    @Mapping(target = "lastName", source = "lastName")
    @Mapping(target = "email", source = "email")
    @Mapping(target = "username", source = "email")
    @Mapping(target = "enabled", source = "active")
    User toKeycloakUser(UsersDTO s);
}
