package com.fundaro.zodiac.taurus.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fundaro.zodiac.taurus.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class ScoresDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(ScoresDTO.class);
        ScoresDTO scoresDTO1 = new ScoresDTO();
        scoresDTO1.setId("1L");
        ScoresDTO scoresDTO2 = new ScoresDTO();
        assertThat(scoresDTO1).isNotEqualTo(scoresDTO2);
        scoresDTO2.setId(scoresDTO1.getId());
        assertThat(scoresDTO1).isEqualTo(scoresDTO2);
        scoresDTO2.setId("2L");
        assertThat(scoresDTO1).isNotEqualTo(scoresDTO2);
        scoresDTO1.setId(null);
        assertThat(scoresDTO1).isNotEqualTo(scoresDTO2);
    }
}
