package com.fundaro.zodiac.taurus.domain;

import static com.fundaro.zodiac.taurus.domain.InstrumentsTestSamples.*;
import static com.fundaro.zodiac.taurus.domain.MediaTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.fundaro.zodiac.taurus.web.rest.TestUtil;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InstrumentsTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Instruments.class);
        Instruments instruments1 = getInstrumentsSample1();
        Instruments instruments2 = new Instruments();
        assertThat(instruments1).isNotEqualTo(instruments2);

        instruments2.setId(instruments1.getId());
        assertThat(instruments1).isEqualTo(instruments2);

        instruments2 = getInstrumentsSample2();
        assertThat(instruments1).isNotEqualTo(instruments2);
    }

    @Test
    void mediaTest() {
        Instruments instruments = getInstrumentsRandomSampleGenerator();
        Media mediaBack = getMediaRandomSampleGenerator();

        instruments.addMedia(mediaBack);
        assertThat(instruments.getMedia()).containsOnly(mediaBack);
        assertThat(mediaBack.getInstrument()).isEqualTo(instruments);

        instruments.removeMedia(mediaBack);
        assertThat(instruments.getMedia()).doesNotContain(mediaBack);
        assertThat(mediaBack.getInstrument()).isNull();

        instruments.media(new HashSet<>(Set.of(mediaBack)));
        assertThat(instruments.getMedia()).containsOnly(mediaBack);
        assertThat(mediaBack.getInstrument()).isEqualTo(instruments);

        instruments.setMedia(new HashSet<>());
        assertThat(instruments.getMedia()).doesNotContain(mediaBack);
        assertThat(mediaBack.getInstrument()).isNull();
    }
}
