package com.fundaro.zodiac.taurus.service.mapper;

import static com.fundaro.zodiac.taurus.domain.PiecesAsserts.*;
import static com.fundaro.zodiac.taurus.domain.PiecesTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PiecesMapperTest {

    private PiecesMapper piecesMapper;

    @BeforeEach
    void setUp() {
        piecesMapper = new PiecesMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getPiecesSample1();
        var actual = piecesMapper.toEntity(piecesMapper.toDto(expected));
        assertPiecesAllPropertiesEquals(expected, actual);
    }
}
