package com.fundaro.zodiac.taurus.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class JpaPageableUtilsTest {

    @Test
    void translatesOpenSearchAndJsonSortPropertiesToJpaProperties() {
        Pageable pageable = PageRequest.of(
            2,
            25,
            Sort.by(Sort.Order.asc("name.keyword"), Sort.Order.desc("start_date"), Sort.Order.asc("insert_date"))
        );

        Pageable normalized = JpaPageableUtils.normalize(pageable);

        assertThat(normalized.getPageNumber()).isEqualTo(2);
        assertThat(normalized.getPageSize()).isEqualTo(25);
        assertThat(normalized.getSort().stream().map(Sort.Order::getProperty)).containsExactly("name", "startDate", "insertDate");
        assertThat(normalized.getSort().stream().map(Sort.Order::getDirection)).containsExactly(
            Sort.Direction.ASC,
            Sort.Direction.DESC,
            Sort.Direction.ASC
        );
    }

    @Test
    void keepsJpaPropertiesUnchanged() {
        List<String> properties = List.of("name", "startDate", "track.name");

        assertThat(properties).allSatisfy(property -> assertThat(JpaPageableUtils.normalizeProperty(property)).isEqualTo(property));
    }
}
