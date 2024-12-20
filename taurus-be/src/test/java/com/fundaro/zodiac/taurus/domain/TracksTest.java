package com.fundaro.zodiac.taurus.domain;

import static com.fundaro.zodiac.taurus.domain.CollectionsTestSamples.*;
import static com.fundaro.zodiac.taurus.domain.LkTrackTypeTestSamples.*;
import static com.fundaro.zodiac.taurus.domain.MediaTestSamples.*;
import static com.fundaro.zodiac.taurus.domain.TracksTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.fundaro.zodiac.taurus.web.rest.TestUtil;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TracksTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Tracks.class);
        Tracks tracks1 = getTracksSample1();
        Tracks tracks2 = new Tracks();
        assertThat(tracks1).isNotEqualTo(tracks2);

        tracks2.setId(tracks1.getId());
        assertThat(tracks1).isEqualTo(tracks2);

        tracks2 = getTracksSample2();
        assertThat(tracks1).isNotEqualTo(tracks2);
    }

    @Test
    void collectionTest() {
        Tracks tracks = getTracksRandomSampleGenerator();
        Collections collectionsBack = getCollectionsRandomSampleGenerator();

        tracks.addCollection(collectionsBack);
        assertThat(tracks.getCollections()).containsOnly(collectionsBack);
        assertThat(collectionsBack.getTrack()).isEqualTo(tracks);

        tracks.removeCollection(collectionsBack);
        assertThat(tracks.getCollections()).doesNotContain(collectionsBack);
        assertThat(collectionsBack.getTrack()).isNull();

        tracks.collections(new HashSet<>(Set.of(collectionsBack)));
        assertThat(tracks.getCollections()).containsOnly(collectionsBack);
        assertThat(collectionsBack.getTrack()).isEqualTo(tracks);

        tracks.setCollections(new HashSet<>());
        assertThat(tracks.getCollections()).doesNotContain(collectionsBack);
        assertThat(collectionsBack.getTrack()).isNull();
    }

    @Test
    void mediaTest() {
        Tracks tracks = getTracksRandomSampleGenerator();
        Media mediaBack = getMediaRandomSampleGenerator();

        tracks.addMedia(mediaBack);
        assertThat(tracks.getMedia()).containsOnly(mediaBack);
        assertThat(mediaBack.getTrack()).isEqualTo(tracks);

        tracks.removeMedia(mediaBack);
        assertThat(tracks.getMedia()).doesNotContain(mediaBack);
        assertThat(mediaBack.getTrack()).isNull();

        tracks.media(new HashSet<>(Set.of(mediaBack)));
        assertThat(tracks.getMedia()).containsOnly(mediaBack);
        assertThat(mediaBack.getTrack()).isEqualTo(tracks);

        tracks.setMedia(new HashSet<>());
        assertThat(tracks.getMedia()).doesNotContain(mediaBack);
        assertThat(mediaBack.getTrack()).isNull();
    }

    @Test
    void typeTest() {
        Tracks tracks = getTracksRandomSampleGenerator();
        LkTrackType lkTrackTypeBack = getLkTrackTypeRandomSampleGenerator();

        tracks.setType(lkTrackTypeBack);
        assertThat(tracks.getType()).isEqualTo(lkTrackTypeBack);

        tracks.type(null);
        assertThat(tracks.getType()).isNull();
    }
}
