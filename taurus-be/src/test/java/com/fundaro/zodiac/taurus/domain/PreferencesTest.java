package com.fundaro.zodiac.taurus.domain;

import static com.fundaro.zodiac.taurus.domain.PreferencesTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.fundaro.zodiac.taurus.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class PreferencesTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Preferences.class);
        Preferences preferences1 = getPreferencesSample1();
        Preferences preferences2 = new Preferences();
        assertThat(preferences1).isNotEqualTo(preferences2);

        preferences2.setId(preferences1.getId());
        assertThat(preferences1).isEqualTo(preferences2);

        preferences2 = getPreferencesSample2();
        assertThat(preferences1).isNotEqualTo(preferences2);
    }
}
