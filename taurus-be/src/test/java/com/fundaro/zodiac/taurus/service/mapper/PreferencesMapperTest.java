package com.fundaro.zodiac.taurus.service.mapper;

import static com.fundaro.zodiac.taurus.domain.PreferencesAsserts.*;
import static com.fundaro.zodiac.taurus.domain.PreferencesTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PreferencesMapperTest {

    private PreferencesMapper preferencesMapper;

    @BeforeEach
    void setUp() {
        preferencesMapper = new PreferencesMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getPreferencesSample1();
        var actual = preferencesMapper.toEntity(preferencesMapper.toDto(expected));
        assertPreferencesAllPropertiesEquals(expected, actual);
    }
}
