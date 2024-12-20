package com.fundaro.zodiac.taurus.domain;

import static com.fundaro.zodiac.taurus.domain.AlbumsTestSamples.*;
import static com.fundaro.zodiac.taurus.domain.CollectionsTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.fundaro.zodiac.taurus.web.rest.TestUtil;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AlbumsTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Albums.class);
        Albums albums1 = getAlbumsSample1();
        Albums albums2 = new Albums();
        assertThat(albums1).isNotEqualTo(albums2);

        albums2.setId(albums1.getId());
        assertThat(albums1).isEqualTo(albums2);

        albums2 = getAlbumsSample2();
        assertThat(albums1).isNotEqualTo(albums2);
    }

    @Test
    void collectionTest() {
        Albums albums = getAlbumsRandomSampleGenerator();
        Collections collectionsBack = getCollectionsRandomSampleGenerator();

        albums.addCollection(collectionsBack);
        assertThat(albums.getCollections()).containsOnly(collectionsBack);
        assertThat(collectionsBack.getAlbum()).isEqualTo(albums);

        albums.removeCollection(collectionsBack);
        assertThat(albums.getCollections()).doesNotContain(collectionsBack);
        assertThat(collectionsBack.getAlbum()).isNull();

        albums.collections(new HashSet<>(Set.of(collectionsBack)));
        assertThat(albums.getCollections()).containsOnly(collectionsBack);
        assertThat(collectionsBack.getAlbum()).isEqualTo(albums);

        albums.setCollections(new HashSet<>());
        assertThat(albums.getCollections()).doesNotContain(collectionsBack);
        assertThat(collectionsBack.getAlbum()).isNull();
    }
}
