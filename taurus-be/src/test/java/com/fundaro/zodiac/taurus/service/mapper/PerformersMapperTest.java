package com.fundaro.zodiac.taurus.service.mapper;

import static com.fundaro.zodiac.taurus.domain.PerformersAsserts.*;
import static com.fundaro.zodiac.taurus.domain.PerformersTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PerformersMapperTest {

    private PerformersMapper performersMapper;

    @BeforeEach
    void setUp() {
        performersMapper = new PerformersMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getPerformersSample1();
        var actual = performersMapper.toEntity(performersMapper.toDto(expected));
        assertPerformersAllPropertiesEquals(expected, actual);
    }
}
