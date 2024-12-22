package com.fundaro.zodiac.taurus.domain;

import static com.fundaro.zodiac.taurus.domain.InstrumentsTestSamples.*;
import static com.fundaro.zodiac.taurus.domain.MediaTestSamples.*;
import static com.fundaro.zodiac.taurus.domain.PerformersTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.fundaro.zodiac.taurus.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class PerformersTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Performers.class);
        Performers performers1 = getPerformersSample1();
        Performers performers2 = new Performers();
        assertThat(performers1).isNotEqualTo(performers2);

        performers2.setId(performers1.getId());
        assertThat(performers1).isEqualTo(performers2);

        performers2 = getPerformersSample2();
        assertThat(performers1).isNotEqualTo(performers2);
    }

    @Test
    void instrumentTest() {
        Performers performers = getPerformersRandomSampleGenerator();
        Instruments instrumentsBack = getInstrumentsRandomSampleGenerator();

        performers.setInstrument(instrumentsBack);
        assertThat(performers.getInstrument()).isEqualTo(instrumentsBack);

        performers.instrument(null);
        assertThat(performers.getInstrument()).isNull();
    }

    @Test
    void mediaTest() {
        Performers performers = getPerformersRandomSampleGenerator();
        Media mediaBack = getMediaRandomSampleGenerator();

        performers.setMedia(mediaBack);
        assertThat(performers.getMedia()).isEqualTo(mediaBack);

        performers.media(null);
        assertThat(performers.getMedia()).isNull();
    }
}
