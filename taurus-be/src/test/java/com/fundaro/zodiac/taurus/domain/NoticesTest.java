package com.fundaro.zodiac.taurus.domain;

import com.fundaro.zodiac.taurus.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

import static com.fundaro.zodiac.taurus.domain.NoticesTestSamples.getNoticesSample1;
import static com.fundaro.zodiac.taurus.domain.NoticesTestSamples.getNoticesSample2;
import static org.assertj.core.api.Assertions.assertThat;

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
