package com.fundaro.zodiac.taurus.service.mapper;

import static com.fundaro.zodiac.taurus.domain.InstrumentsAsserts.*;
import static com.fundaro.zodiac.taurus.domain.InstrumentsTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class InstrumentsMapperTest {

    private InstrumentsMapper instrumentsMapper;

    @BeforeEach
    void setUp() {
        instrumentsMapper = new InstrumentsMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getInstrumentsSample1();
        var actual = instrumentsMapper.toEntity(instrumentsMapper.toDto(expected));
        assertInstrumentsAllPropertiesEquals(expected, actual);
    }
}
