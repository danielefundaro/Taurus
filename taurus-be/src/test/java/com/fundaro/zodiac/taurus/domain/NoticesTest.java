package com.fundaro.zodiac.taurus.domain;

import static com.fundaro.zodiac.taurus.domain.NoticesTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.fundaro.zodiac.taurus.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class NoticesTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Notices.class);
        Notices notices1 = getNoticesSample1();
        Notices notices2 = new Notices();
        assertThat(notices1).isNotEqualTo(notices2);

        notices2.setId(notices1.getId());
        assertThat(notices1).isEqualTo(notices2);

        notices2 = getNoticesSample2();
        assertThat(notices1).isNotEqualTo(notices2);
    }
}
