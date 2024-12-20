package com.fundaro.zodiac.taurus.service.mapper;

import com.fundaro.zodiac.taurus.domain.Preferences;
import com.fundaro.zodiac.taurus.service.dto.PreferencesDTO;
import org.mapstruct.*;

/**
 * Mapper for the entity {@link Preferences} and its DTO {@link PreferencesDTO}.
 */
@Mapper(componentModel = "spring")
public interface PreferencesMapper extends EntityMapper<PreferencesDTO, Preferences> {}
