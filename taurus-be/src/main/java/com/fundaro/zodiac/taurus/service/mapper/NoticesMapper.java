package com.fundaro.zodiac.taurus.service.mapper;

import com.fundaro.zodiac.taurus.domain.Notices;
import com.fundaro.zodiac.taurus.service.dto.NoticesDTO;
import org.mapstruct.Mapper;

/**
 * Mapper for the entity {@link Notices} and its DTO {@link NoticesDTO}.
 */
@Mapper(componentModel = "spring")
public interface NoticesMapper extends EntityMapper<NoticesDTO, Notices> {}
