package com.fundaro.zodiac.taurus.service.mapper;

import static com.fundaro.zodiac.taurus.domain.NoticesTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(actual)
            .usingRecursiveComparison()
            .ignoringFields("insertBy", "insertDate", "editBy", "editDate")
            .isEqualTo(expected);
    }
}
