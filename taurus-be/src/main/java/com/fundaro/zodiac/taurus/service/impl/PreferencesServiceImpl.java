package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.Preferences;
import com.fundaro.zodiac.taurus.domain.criteria.PreferencesCriteria;
import com.fundaro.zodiac.taurus.repository.PreferencesRepository;
import com.fundaro.zodiac.taurus.service.PreferencesService;
import com.fundaro.zodiac.taurus.service.dto.PreferencesDTO;
import com.fundaro.zodiac.taurus.service.mapper.PreferencesMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service Implementation for managing {@link com.fundaro.zodiac.taurus.domain.Preferences}.
 */
@Service
@Transactional
public class PreferencesServiceImpl extends CommonServiceImpl<Preferences, PreferencesDTO, PreferencesCriteria, PreferencesMapper, PreferencesRepository> implements PreferencesService {

    public PreferencesServiceImpl(PreferencesRepository preferencesRepository, PreferencesMapper preferencesMapper) {
        super(preferencesRepository, preferencesMapper, PreferencesService.class, Preferences.class.getSimpleName());
    }
}
