package com.fundaro.zodiac.taurus.domain;

import static com.fundaro.zodiac.taurus.domain.MediaTestSamples.*;
import static com.fundaro.zodiac.taurus.domain.PiecesTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.fundaro.zodiac.taurus.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class PiecesTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Pieces.class);
        Pieces pieces1 = getPiecesSample1();
        Pieces pieces2 = new Pieces();
        assertThat(pieces1).isNotEqualTo(pieces2);

        pieces2.setId(pieces1.getId());
        assertThat(pieces1).isEqualTo(pieces2);

        pieces2 = getPiecesSample2();
        assertThat(pieces1).isNotEqualTo(pieces2);
    }

    @Test
    void mediaTest() {
        Pieces pieces = getPiecesRandomSampleGenerator();
        Media mediaBack = getMediaRandomSampleGenerator();

        pieces.setMedia(mediaBack);
        assertThat(pieces.getMedia()).isEqualTo(mediaBack);

        pieces.media(null);
        assertThat(pieces.getMedia()).isNull();
    }
}
