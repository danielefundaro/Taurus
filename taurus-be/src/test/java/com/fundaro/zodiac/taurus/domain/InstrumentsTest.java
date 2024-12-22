package com.fundaro.zodiac.taurus.domain;

import static com.fundaro.zodiac.taurus.domain.InstrumentsTestSamples.*;
import static com.fundaro.zodiac.taurus.domain.PerformersTestSamples.*;
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
    void performerTest() {
        Instruments instruments = getInstrumentsRandomSampleGenerator();
        Performers performersBack = getPerformersRandomSampleGenerator();

        instruments.addPerformer(performersBack);
        assertThat(instruments.getPerformers()).containsOnly(performersBack);
        assertThat(performersBack.getInstrument()).isEqualTo(instruments);

        instruments.removePerformer(performersBack);
        assertThat(instruments.getPerformers()).doesNotContain(performersBack);
        assertThat(performersBack.getInstrument()).isNull();

        instruments.performers(new HashSet<>(Set.of(performersBack)));
        assertThat(instruments.getPerformers()).containsOnly(performersBack);
        assertThat(performersBack.getInstrument()).isEqualTo(instruments);

        instruments.setPerformers(new HashSet<>());
        assertThat(instruments.getPerformers()).doesNotContain(performersBack);
        assertThat(performersBack.getInstrument()).isNull();
    }
}
