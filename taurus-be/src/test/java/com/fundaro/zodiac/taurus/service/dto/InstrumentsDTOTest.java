package com.fundaro.zodiac.taurus.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fundaro.zodiac.taurus.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class InstrumentsDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(InstrumentsDTO.class);
        InstrumentsDTO instrumentsDTO1 = new InstrumentsDTO();
        instrumentsDTO1.setId(1L);
        InstrumentsDTO instrumentsDTO2 = new InstrumentsDTO();
        assertThat(instrumentsDTO1).isNotEqualTo(instrumentsDTO2);
        instrumentsDTO2.setId(instrumentsDTO1.getId());
        assertThat(instrumentsDTO1).isEqualTo(instrumentsDTO2);
        instrumentsDTO2.setId(2L);
        assertThat(instrumentsDTO1).isNotEqualTo(instrumentsDTO2);
        instrumentsDTO1.setId(null);
        assertThat(instrumentsDTO1).isNotEqualTo(instrumentsDTO2);
    }
}
