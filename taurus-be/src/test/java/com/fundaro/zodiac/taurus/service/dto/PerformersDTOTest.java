package com.fundaro.zodiac.taurus.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fundaro.zodiac.taurus.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class PerformersDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(PerformersDTO.class);
        PerformersDTO performersDTO1 = new PerformersDTO();
        performersDTO1.setId(1L);
        PerformersDTO performersDTO2 = new PerformersDTO();
        assertThat(performersDTO1).isNotEqualTo(performersDTO2);
        performersDTO2.setId(performersDTO1.getId());
        assertThat(performersDTO1).isEqualTo(performersDTO2);
        performersDTO2.setId(2L);
        assertThat(performersDTO1).isNotEqualTo(performersDTO2);
        performersDTO1.setId(null);
        assertThat(performersDTO1).isNotEqualTo(performersDTO2);
    }
}
