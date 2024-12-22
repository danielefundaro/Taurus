package com.fundaro.zodiac.taurus.domain;

import static com.fundaro.zodiac.taurus.domain.MediaTestSamples.*;
import static com.fundaro.zodiac.taurus.domain.PerformersTestSamples.*;
import static com.fundaro.zodiac.taurus.domain.PiecesTestSamples.*;
import static com.fundaro.zodiac.taurus.domain.TracksTestSamples.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.fundaro.zodiac.taurus.web.rest.TestUtil;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MediaTest {

    @Test
    void equalsVerifier() throws Exception {
        TestUtil.equalsVerifier(Media.class);
        Media media1 = getMediaSample1();
        Media media2 = new Media();
        assertThat(media1).isNotEqualTo(media2);

        media2.setId(media1.getId());
        assertThat(media1).isEqualTo(media2);

        media2 = getMediaSample2();
        assertThat(media1).isNotEqualTo(media2);
    }

    @Test
    void performerTest() {
        Media media = getMediaRandomSampleGenerator();
        Performers performersBack = getPerformersRandomSampleGenerator();

        media.addPerformer(performersBack);
        assertThat(media.getPerformers()).containsOnly(performersBack);
        assertThat(performersBack.getMedia()).isEqualTo(media);

        media.removePerformer(performersBack);
        assertThat(media.getPerformers()).doesNotContain(performersBack);
        assertThat(performersBack.getMedia()).isNull();

        media.performers(new HashSet<>(Set.of(performersBack)));
        assertThat(media.getPerformers()).containsOnly(performersBack);
        assertThat(performersBack.getMedia()).isEqualTo(media);

        media.setPerformers(new HashSet<>());
        assertThat(media.getPerformers()).doesNotContain(performersBack);
        assertThat(performersBack.getMedia()).isNull();
    }

    @Test
    void pieceTest() {
        Media media = getMediaRandomSampleGenerator();
        Pieces piecesBack = getPiecesRandomSampleGenerator();

        media.addPiece(piecesBack);
        assertThat(media.getPieces()).containsOnly(piecesBack);
        assertThat(piecesBack.getMedia()).isEqualTo(media);

        media.removePiece(piecesBack);
        assertThat(media.getPieces()).doesNotContain(piecesBack);
        assertThat(piecesBack.getMedia()).isNull();

        media.pieces(new HashSet<>(Set.of(piecesBack)));
        assertThat(media.getPieces()).containsOnly(piecesBack);
        assertThat(piecesBack.getMedia()).isEqualTo(media);

        media.setPieces(new HashSet<>());
        assertThat(media.getPieces()).doesNotContain(piecesBack);
        assertThat(piecesBack.getMedia()).isNull();
    }

    @Test
    void trackTest() {
        Media media = getMediaRandomSampleGenerator();
        Tracks tracksBack = getTracksRandomSampleGenerator();

        media.setTrack(tracksBack);
        assertThat(media.getTrack()).isEqualTo(tracksBack);

        media.track(null);
        assertThat(media.getTrack()).isNull();
    }
}
