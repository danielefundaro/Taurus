package com.fundaro.zodiac.taurus.service.mapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.fundaro.zodiac.taurus.domain.PreferencesTestSamples.getPreferencesSample1;
import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(actual)
            .usingRecursiveComparison()
            .ignoringFields("insertBy", "insertDate", "editBy", "editDate")
            .isEqualTo(expected);
    }
}
