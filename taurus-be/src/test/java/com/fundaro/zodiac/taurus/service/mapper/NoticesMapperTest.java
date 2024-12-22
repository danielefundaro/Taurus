package com.fundaro.zodiac.taurus.service.mapper;

import static com.fundaro.zodiac.taurus.domain.NoticesAsserts.*;
import static com.fundaro.zodiac.taurus.domain.NoticesTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NoticesMapperTest {

    private NoticesMapper noticesMapper;

    @BeforeEach
    void setUp() {
        noticesMapper = new NoticesMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getNoticesSample1();
        var actual = noticesMapper.toEntity(noticesMapper.toDto(expected));
        assertNoticesAllPropertiesEquals(expected, actual);
    }
}
