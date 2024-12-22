package com.fundaro.zodiac.taurus.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fundaro.zodiac.taurus.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class LastResearchDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(LastResearchDTO.class);
        LastResearchDTO lastResearchDTO1 = new LastResearchDTO();
        lastResearchDTO1.setId(1L);
        LastResearchDTO lastResearchDTO2 = new LastResearchDTO();
        assertThat(lastResearchDTO1).isNotEqualTo(lastResearchDTO2);
        lastResearchDTO2.setId(lastResearchDTO1.getId());
        assertThat(lastResearchDTO1).isEqualTo(lastResearchDTO2);
        lastResearchDTO2.setId(2L);
        assertThat(lastResearchDTO1).isNotEqualTo(lastResearchDTO2);
        lastResearchDTO1.setId(null);
        assertThat(lastResearchDTO1).isNotEqualTo(lastResearchDTO2);
    }
}
