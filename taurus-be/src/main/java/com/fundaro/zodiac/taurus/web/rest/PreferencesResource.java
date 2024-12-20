package com.fundaro.zodiac.taurus.web.rest;

import com.fundaro.zodiac.taurus.domain.Preferences;
import com.fundaro.zodiac.taurus.domain.criteria.PreferencesCriteria;
import com.fundaro.zodiac.taurus.service.PreferencesService;
import com.fundaro.zodiac.taurus.service.dto.PreferencesDTO;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for managing {@link com.fundaro.zodiac.taurus.domain.Preferences}.
 */
@RestController
@RequestMapping("/api/preferences")
public class PreferencesResource extends CommonResource<Preferences, PreferencesDTO, PreferencesCriteria, PreferencesService> {

    public PreferencesResource(PreferencesService preferencesService) {
        super(preferencesService, PreferencesResource.class, Preferences.class.getSimpleName());
    }
}
