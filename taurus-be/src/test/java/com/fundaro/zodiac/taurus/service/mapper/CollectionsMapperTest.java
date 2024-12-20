package com.fundaro.zodiac.taurus.service.mapper;

import static com.fundaro.zodiac.taurus.domain.CollectionsAsserts.*;
import static com.fundaro.zodiac.taurus.domain.CollectionsTestSamples.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CollectionsMapperTest {

    private CollectionsMapper collectionsMapper;

    @BeforeEach
    void setUp() {
        collectionsMapper = new CollectionsMapperImpl();
    }

    @Test
    void shouldConvertToDtoAndBack() {
        var expected = getCollectionsSample1();
        var actual = collectionsMapper.toEntity(collectionsMapper.toDto(expected));
        assertCollectionsAllPropertiesEquals(expected, actual);
    }
}
