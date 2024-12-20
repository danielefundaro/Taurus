package com.fundaro.zodiac.taurus.service.mapper;

import static com.fundaro.zodiac.taurus.domain.TracksAsserts.*;
import static com.fundaro.zodiac.taurus.domain.TracksTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TracksMapperTest {

    private TracksMapper tracksMapper;

    @BeforeEach
    void setUp() {
        tracksMapper = new TracksMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getTracksSample1();
        var actual = tracksMapper.toEntity(tracksMapper.toDto(expected));
        assertTracksAllPropertiesEquals(expected, actual);
    }
}
