package com.fundaro.zodiac.taurus.service.mapper;

import static com.fundaro.zodiac.taurus.domain.LkTrackTypeAsserts.*;
import static com.fundaro.zodiac.taurus.domain.LkTrackTypeTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LkTrackTypeMapperTest {

    private LkTrackTypeMapper lkTrackTypeMapper;

    @BeforeEach
    void setUp() {
        lkTrackTypeMapper = new LkTrackTypeMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getLkTrackTypeSample1();
        var actual = lkTrackTypeMapper.toEntity(lkTrackTypeMapper.toDto(expected));
        assertLkTrackTypeAllPropertiesEquals(expected, actual);
    }
}
