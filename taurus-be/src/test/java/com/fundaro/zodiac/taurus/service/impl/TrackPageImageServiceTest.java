package com.fundaro.zodiac.taurus.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundaro.zodiac.taurus.domain.Media;
import com.fundaro.zodiac.taurus.domain.SheetsMusic;
import com.fundaro.zodiac.taurus.domain.Tracks;
import com.fundaro.zodiac.taurus.repository.MediaRepository;
import com.fundaro.zodiac.taurus.repository.TrackPageEditReceiptRepository;
import com.fundaro.zodiac.taurus.repository.TracksRepository;
import com.fundaro.zodiac.taurus.service.MediaService;
import com.fundaro.zodiac.taurus.service.dto.ChildrenEntitiesDTO;
import com.fundaro.zodiac.taurus.service.dto.MediaDTO;
import com.fundaro.zodiac.taurus.service.dto.TrackPageImageDTOs;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TrackPageImageServiceTest {

    @Test
    void replacesTheRequestedPageWithEveryCropResult() throws Exception {
        TracksRepository tracks = mock(TracksRepository.class);
        MediaRepository media = mock(MediaRepository.class);
        TrackPageEditReceiptRepository receipts = mock(TrackPageEditReceiptRepository.class);
        MediaService mediaService = mock(MediaService.class);
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        TrackPageImageService service = new TrackPageImageService(
            tracks,
            media,
            receipts,
            mediaService,
            new ImageTransformationService(),
            new ObjectMapper(),
            transactions
        );

        Tracks track = new Tracks();
        track.setId(4L);
        track.setEntityVersion(2L);
        SheetsMusic score = new SheetsMusic();
        score.setId(5L);
        Media oldMedia = new Media();
        oldMedia.setId(6L);
        score.getMedia().add(oldMedia);
        Media followingMedia = new Media();
        followingMedia.setId(7L);
        score.getMedia().add(followingMedia);
        track.getScores().add(score);
        Media firstNewMedia = new Media();
        firstNewMedia.setId(8L);
        Media secondNewMedia = new Media();
        secondNewMedia.setId(9L);
        MediaDTO firstStored = new MediaDTO();
        firstStored.setId(8L);
        firstStored.setName("page-edited-1.png");
        MediaDTO secondStored = new MediaDTO();
        secondStored.setId(9L);
        secondStored.setName("page-edited-2.png");
        byte[] source = png();
        JwtAuthenticationToken token = authentication();

        when(receipts.findByRequestedByAndRequestKey(any(), any())).thenReturn(Optional.empty());
        when(tracks.findByIdAndDeletedFalse(4L)).thenReturn(Optional.of(track));
        when(mediaService.getContent(6L, token)).thenReturn(new MediaService.MediaContent("page.png", "image/png", source.length, source));
        when(mediaService.store(any(), any(), any(), any(), any())).thenReturn(firstStored, secondStored);
        when(media.getReferenceById(8L)).thenReturn(firstNewMedia);
        when(media.getReferenceById(9L)).thenReturn(secondNewMedia);
        when(tracks.saveAndFlush(track)).thenAnswer(invocation -> {
            track.setEntityVersion(3L);
            return track;
        });

        TrackPageImageDTOs.EditResult result = service.edit(
            4L,
            5L,
            6L,
            UUID.fromString("b5b0ec70-0f1d-4e1a-b6c7-d8975b20fb6b"),
            new TrackPageImageDTOs.EditRequest(
                2L,
                1,
                0,
                0,
                List.of(new TrackPageImageDTOs.Crop(0, 0, 0.5, 1), new TrackPageImageDTOs.Crop(0.5, 0, 0.5, 1)),
                false,
                0,
                0,
                false,
                null
            ),
            token
        );

        assertThat(result.trackVersion()).isEqualTo(3L);
        assertThat(result.media()).extracting(ChildrenEntitiesDTO::getIndex).containsExactly(8L, 9L);
        assertThat(result.media()).extracting(ChildrenEntitiesDTO::getOrder).containsExactly(1L, 2L);
        assertThat(score.getMedia()).containsExactly(firstNewMedia, secondNewMedia, followingMedia);
        verify(tracks).flush();
        verify(mediaService).deleteIfUnreferenced(6L, token);
        verify(receipts).saveAndFlush(any());
    }

    private static byte[] png() throws Exception {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(new BufferedImage(20, 30, BufferedImage.TYPE_INT_RGB), "png", output);
            return output.toByteArray();
        }
    }

    private static JwtAuthenticationToken authentication() {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject("user-1")
            .claim("tenant", "BMCDG")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .build();
        return new JwtAuthenticationToken(jwt, java.util.List.of(new SimpleGrantedAuthority("ROLE_ARCHIVIST")));
    }
}
