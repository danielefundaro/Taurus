package com.fundaro.zodiac.taurus.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fundaro.zodiac.taurus.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class TracksDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(TracksDTO.class);
        TracksDTO tracksDTO1 = new TracksDTO();
        tracksDTO1.setId("1L");
        TracksDTO tracksDTO2 = new TracksDTO();
        assertThat(tracksDTO1).isNotEqualTo(tracksDTO2);
        tracksDTO2.setId(tracksDTO1.getId());
        assertThat(tracksDTO1).isEqualTo(tracksDTO2);
        tracksDTO2.setId("2L");
        assertThat(tracksDTO1).isNotEqualTo(tracksDTO2);
        tracksDTO1.setId(null);
        assertThat(tracksDTO1).isNotEqualTo(tracksDTO2);
    }
}
