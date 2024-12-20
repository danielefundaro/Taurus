package com.fundaro.zodiac.taurus.service.mapper;

import static com.fundaro.zodiac.taurus.domain.AlbumsAsserts.*;
import static com.fundaro.zodiac.taurus.domain.AlbumsTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AlbumsMapperTest {

    private AlbumsMapper albumsMapper;

    @BeforeEach
    void setUp() {
        albumsMapper = new AlbumsMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getAlbumsSample1();
        var actual = albumsMapper.toEntity(albumsMapper.toDto(expected));
        assertAlbumsAllPropertiesEquals(expected, actual);
    }
}
