package com.fundaro.zodiac.taurus.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fundaro.zodiac.taurus.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class CollectionsDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(CollectionsDTO.class);
        CollectionsDTO collectionsDTO1 = new CollectionsDTO();
        collectionsDTO1.setId(1L);
        CollectionsDTO collectionsDTO2 = new CollectionsDTO();
        assertThat(collectionsDTO1).isNotEqualTo(collectionsDTO2);
        collectionsDTO2.setId(collectionsDTO1.getId());
        assertThat(collectionsDTO1).isEqualTo(collectionsDTO2);
        collectionsDTO2.setId(2L);
        assertThat(collectionsDTO1).isNotEqualTo(collectionsDTO2);
        collectionsDTO1.setId(null);
        assertThat(collectionsDTO1).isNotEqualTo(collectionsDTO2);
    }
}
