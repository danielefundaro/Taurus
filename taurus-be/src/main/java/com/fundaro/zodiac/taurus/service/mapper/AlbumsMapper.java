package com.fundaro.zodiac.taurus.service.mapper;

import com.fundaro.zodiac.taurus.domain.Albums;
import com.fundaro.zodiac.taurus.service.dto.AlbumsDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link Albums} and its DTO {@link AlbumsDTO}.
 */
@Mapper(componentModel = "spring")
public interface AlbumsMapper extends EntityMapper<AlbumsDTO, Albums> {}
