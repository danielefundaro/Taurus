package com.fundaro.zodiac.taurus.service.mapper;

import static com.fundaro.zodiac.taurus.domain.LastResearchTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

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
        assertThat(actual)
            .usingRecursiveComparison()
            .ignoringFields("insertBy", "insertDate", "editBy", "editDate")
            .isEqualTo(expected);
    }
}
