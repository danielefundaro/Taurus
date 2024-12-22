package com.fundaro.zodiac.taurus.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fundaro.zodiac.taurus.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class NoticesDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(NoticesDTO.class);
        NoticesDTO noticesDTO1 = new NoticesDTO();
        noticesDTO1.setId(1L);
        NoticesDTO noticesDTO2 = new NoticesDTO();
        assertThat(noticesDTO1).isNotEqualTo(noticesDTO2);
        noticesDTO2.setId(noticesDTO1.getId());
        assertThat(noticesDTO1).isEqualTo(noticesDTO2);
        noticesDTO2.setId(2L);
        assertThat(noticesDTO1).isNotEqualTo(noticesDTO2);
        noticesDTO1.setId(null);
        assertThat(noticesDTO1).isNotEqualTo(noticesDTO2);
    }
}
