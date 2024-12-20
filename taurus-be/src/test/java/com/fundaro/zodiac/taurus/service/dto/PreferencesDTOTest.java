package com.fundaro.zodiac.taurus.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fundaro.zodiac.taurus.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class PreferencesDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(PreferencesDTO.class);
        PreferencesDTO preferencesDTO1 = new PreferencesDTO();
        preferencesDTO1.setId(1L);
        PreferencesDTO preferencesDTO2 = new PreferencesDTO();
        assertThat(preferencesDTO1).isNotEqualTo(preferencesDTO2);
        preferencesDTO2.setId(preferencesDTO1.getId());
        assertThat(preferencesDTO1).isEqualTo(preferencesDTO2);
        preferencesDTO2.setId(2L);
        assertThat(preferencesDTO1).isNotEqualTo(preferencesDTO2);
        preferencesDTO1.setId(null);
        assertThat(preferencesDTO1).isNotEqualTo(preferencesDTO2);
    }
}
