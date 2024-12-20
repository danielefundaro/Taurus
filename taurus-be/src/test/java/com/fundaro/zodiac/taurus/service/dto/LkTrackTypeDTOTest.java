package com.fundaro.zodiac.taurus.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fundaro.zodiac.taurus.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class LkTrackTypeDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(LkTrackTypeDTO.class);
        LkTrackTypeDTO lkTrackTypeDTO1 = new LkTrackTypeDTO();
        lkTrackTypeDTO1.setId(1L);
        LkTrackTypeDTO lkTrackTypeDTO2 = new LkTrackTypeDTO();
        assertThat(lkTrackTypeDTO1).isNotEqualTo(lkTrackTypeDTO2);
        lkTrackTypeDTO2.setId(lkTrackTypeDTO1.getId());
        assertThat(lkTrackTypeDTO1).isEqualTo(lkTrackTypeDTO2);
        lkTrackTypeDTO2.setId(2L);
        assertThat(lkTrackTypeDTO1).isNotEqualTo(lkTrackTypeDTO2);
        lkTrackTypeDTO1.setId(null);
        assertThat(lkTrackTypeDTO1).isNotEqualTo(lkTrackTypeDTO2);
    }
}
