package com.fundaro.zodiac.taurus.service.mapper;

import static com.fundaro.zodiac.taurus.domain.MediaAsserts.*;
import static com.fundaro.zodiac.taurus.domain.MediaTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MediaMapperTest {

    private MediaMapper mediaMapper;

    @BeforeEach
    void setUp() {
        mediaMapper = new MediaMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getMediaSample1();
        var actual = mediaMapper.toEntity(mediaMapper.toDto(expected));
        assertMediaAllPropertiesEquals(expected, actual);
    }
}
