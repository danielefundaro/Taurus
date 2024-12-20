package com.fundaro.zodiac.taurus.domain;

import static com.fundaro.zodiac.taurus.domain.AlbumsTestSamples.*;
import static com.fundaro.zodiac.taurus.domain.CollectionsTestSamples.*;
import static com.fundaro.zodiac.taurus.domain.TracksTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.fundaro.zodiac.taurus.web.rest.TestUtil;
import org.junit.jupiter.api.Test;

class CollectionsTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Collections.class);
        Collections collections1 = getCollectionsSample1();
        Collections collections2 = new Collections();
        assertThat(collections1).isNotEqualTo(collections2);

        collections2.setId(collections1.getId());
        assertThat(collections1).isEqualTo(collections2);

        collections2 = getCollectionsSample2();
        assertThat(collections1).isNotEqualTo(collections2);
    }

    @Test
    void albumTest() {
        Collections collections = getCollectionsRandomSampleGenerator();
        Albums albumsBack = getAlbumsRandomSampleGenerator();

        collections.setAlbum(albumsBack);
        assertThat(collections.getAlbum()).isEqualTo(albumsBack);

        collections.album(null);
        assertThat(collections.getAlbum()).isNull();
    }

    @Test
    void trackTest() {
        Collections collections = getCollectionsRandomSampleGenerator();
        Tracks tracksBack = getTracksRandomSampleGenerator();

        collections.setTrack(tracksBack);
        assertThat(collections.getTrack()).isEqualTo(tracksBack);

        collections.track(null);
        assertThat(collections.getTrack()).isNull();
    }
}
