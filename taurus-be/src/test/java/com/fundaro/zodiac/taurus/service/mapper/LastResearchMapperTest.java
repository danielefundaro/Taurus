package com.fundaro.zodiac.taurus.service.mapper;

import static com.fundaro.zodiac.taurus.domain.LastResearchAsserts.*;
import static com.fundaro.zodiac.taurus.domain.LastResearchTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LastResearchMapperTest {

    private LastResearchMapper lastResearchMapper;

    @BeforeEach
    void setUp() {
        lastResearchMapper = new LastResearchMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getLastResearchSample1();
        var actual = lastResearchMapper.toEntity(lastResearchMapper.toDto(expected));
        assertLastResearchAllPropertiesEquals(expected, actual);
    }
}
