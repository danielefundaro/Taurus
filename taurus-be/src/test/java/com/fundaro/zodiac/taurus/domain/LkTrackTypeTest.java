package com.fundaro.zodiac.taurus.domain;

import static com.fundaro.zodiac.taurus.domain.LkTrackTypeTestSamples.*;
import static com.fundaro.zodiac.taurus.domain.TracksTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.fundaro.zodiac.taurus.web.rest.TestUtil;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class LkTrackTypeTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(LkTrackType.class);
        LkTrackType lkTrackType1 = getLkTrackTypeSample1();
        LkTrackType lkTrackType2 = new LkTrackType();
        assertThat(lkTrackType1).isNotEqualTo(lkTrackType2);

        lkTrackType2.setId(lkTrackType1.getId());
        assertThat(lkTrackType1).isEqualTo(lkTrackType2);

        lkTrackType2 = getLkTrackTypeSample2();
        assertThat(lkTrackType1).isNotEqualTo(lkTrackType2);
    }

    @Test
    void trackTest() {
        LkTrackType lkTrackType = getLkTrackTypeRandomSampleGenerator();
        Tracks tracksBack = getTracksRandomSampleGenerator();

        lkTrackType.addTrack(tracksBack);
        assertThat(lkTrackType.getTracks()).containsOnly(tracksBack);
        assertThat(tracksBack.getType()).isEqualTo(lkTrackType);

        lkTrackType.removeTrack(tracksBack);
        assertThat(lkTrackType.getTracks()).doesNotContain(tracksBack);
        assertThat(tracksBack.getType()).isNull();

        lkTrackType.tracks(new HashSet<>(Set.of(tracksBack)));
        assertThat(lkTrackType.getTracks()).containsOnly(tracksBack);
        assertThat(tracksBack.getType()).isEqualTo(lkTrackType);

        lkTrackType.setTracks(new HashSet<>());
        assertThat(lkTrackType.getTracks()).doesNotContain(tracksBack);
        assertThat(tracksBack.getType()).isNull();
    }
}
