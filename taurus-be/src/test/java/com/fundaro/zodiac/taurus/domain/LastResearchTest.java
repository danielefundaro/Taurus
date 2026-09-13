package com.fundaro.zodiac.taurus.domain;

import com.fundaro.zodiac.taurus.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

import static com.fundaro.zodiac.taurus.domain.LastResearchTestSamples.getLastResearchSample1;
import static com.fundaro.zodiac.taurus.domain.LastResearchTestSamples.getLastResearchSample2;
import static org.assertj.core.api.Assertions.assertThat;

class LastResearchTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(LastResearch.class);
        LastResearch lastResearch1 = getLastResearchSample1();
        LastResearch lastResearch2 = new LastResearch();
        assertThat(lastResearch1).isNotEqualTo(lastResearch2);

        lastResearch2.setId(lastResearch1.getId());
        assertThat(lastResearch1).isEqualTo(lastResearch2);

        lastResearch2 = getLastResearchSample2();
        assertThat(lastResearch1).isNotEqualTo(lastResearch2);
    }
}
