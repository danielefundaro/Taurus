package com.fundaro.zodiac.taurus.service.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fundaro.zodiac.taurus.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class PiecesDTOTest {

    @Test
    void dtoEqualsVerifier() throws Exception {
        TestUtil.equalsVerifier(PiecesDTO.class);
        PiecesDTO piecesDTO1 = new PiecesDTO();
        piecesDTO1.setId(1L);
        PiecesDTO piecesDTO2 = new PiecesDTO();
        assertThat(piecesDTO1).isNotEqualTo(piecesDTO2);
        piecesDTO2.setId(piecesDTO1.getId());
        assertThat(piecesDTO1).isEqualTo(piecesDTO2);
        piecesDTO2.setId(2L);
        assertThat(piecesDTO1).isNotEqualTo(piecesDTO2);
        piecesDTO1.setId(null);
        assertThat(piecesDTO1).isNotEqualTo(piecesDTO2);
    }
}
