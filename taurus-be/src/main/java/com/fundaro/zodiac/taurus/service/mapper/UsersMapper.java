package com.fundaro.zodiac.taurus.service.mapper;

import com.fundaro.zodiac.taurus.domain.Users;
import com.fundaro.zodiac.taurus.service.dto.UsersDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper for the entity {@link Users} and its DTO {@link UsersDTO}.
 */
@Mapper(componentModel = "spring")
public interface UsersMapper extends EntityOpenSearchMapper<UsersDTO, Users> {
    @Mapping(target = "instruments", source = "instruments", qualifiedByName = "orderChildrenToDto")
    UsersDTO toDto(Users s);
}
