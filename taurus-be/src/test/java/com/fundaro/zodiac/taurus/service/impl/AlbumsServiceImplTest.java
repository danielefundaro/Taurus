package com.fundaro.zodiac.taurus.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fundaro.zodiac.taurus.domain.Albums;
import com.fundaro.zodiac.taurus.domain.Tracks;
import com.fundaro.zodiac.taurus.repository.AlbumsRepository;
import com.fundaro.zodiac.taurus.repository.TracksRepository;
import com.fundaro.zodiac.taurus.service.dto.AlbumsDTO;
import com.fundaro.zodiac.taurus.service.dto.ChildrenEntitiesDTO;
import com.fundaro.zodiac.taurus.service.mapper.AlbumsMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class AlbumsServiceImplTest {

    @Test
    void updatesTheManagedTracksCollectionWithoutReplacingIt() {
        AlbumsRepository repository = mock(AlbumsRepository.class);
        AlbumsMapper mapper = mock(AlbumsMapper.class);
        TracksRepository tracksRepository = mock(TracksRepository.class);
        AlbumsServiceImpl service = new AlbumsServiceImpl(repository, mapper, tracksRepository);
        Albums entity = new Albums();
        entity.setId(1L);
        Tracks oldTrack = new Tracks();
        oldTrack.setId(8L);
        entity.getTracks().add(oldTrack);
        List<Tracks> managedTracks = entity.getTracks();
        Tracks replacement = new Tracks();
        replacement.setId(9L);
        ChildrenEntitiesDTO trackReference = new ChildrenEntitiesDTO();
        trackReference.setIndex(9L);
        trackReference.setOrder(1L);
        AlbumsDTO request = new AlbumsDTO();
        request.setId(1L);
        request.setTracks(Set.of(trackReference));

        when(repository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(entity));
        when(tracksRepository.getReferenceById(9L)).thenReturn(replacement);
        when(repository.save(entity)).thenReturn(entity);
        when(mapper.toDto(entity)).thenReturn(new AlbumsDTO());
        org.mockito.Mockito.doAnswer(invocation -> {
            assertThat(entity.getTracks()).isEmpty();
            return null;
        }).when(repository).flush();

        service.update(1L, request, authentication());

        verify(repository).flush();
        assertThat(entity.getTracks()).isSameAs(managedTracks).containsExactly(replacement);
    }

    @Test
    void keepsAssignedAlbumTracksMutable() {
        Albums album = new Albums();
        album.setTracks(List.of(new Tracks()));

        album.getTracks().clear();

        assertThat(album.getTracks()).isEmpty();
    }

    private JwtAuthenticationToken authentication() {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject("user-1")
            .claim("tenant", "BMCDG")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .build();
        return new JwtAuthenticationToken(jwt);
    }
}
