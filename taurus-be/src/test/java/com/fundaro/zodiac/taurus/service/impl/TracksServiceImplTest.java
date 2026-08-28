package com.fundaro.zodiac.taurus.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fundaro.zodiac.taurus.domain.SheetsMusic;
import com.fundaro.zodiac.taurus.domain.Tracks;
import com.fundaro.zodiac.taurus.rabbitmq.Sender;
import com.fundaro.zodiac.taurus.repository.InstrumentsRepository;
import com.fundaro.zodiac.taurus.repository.MediaRepository;
import com.fundaro.zodiac.taurus.repository.TracksRepository;
import com.fundaro.zodiac.taurus.service.QueueUploadFilesService;
import com.fundaro.zodiac.taurus.service.dto.QueueUploadFilesDTO;
import com.fundaro.zodiac.taurus.service.dto.SheetsMusicDTO;
import com.fundaro.zodiac.taurus.service.dto.TracksDTO;
import com.fundaro.zodiac.taurus.service.mapper.TracksMapper;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

class TracksServiceImplTest {

    @AfterEach
    void clearTransactionSynchronization() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void publishesUploadJobOnlyAfterCommit() {
        TracksRepository repository = mock(TracksRepository.class);
        TracksMapper mapper = mock(TracksMapper.class);
        QueueUploadFilesService queueUploadFilesService = mock(QueueUploadFilesService.class);
        Sender sender = mock(Sender.class);
        TracksServiceImpl service = new TracksServiceImpl(
            repository,
            mapper,
            queueUploadFilesService,
            mock(MediaRepository.class),
            mock(InstrumentsRepository.class),
            sender
        );
        JwtAuthenticationToken token = authentication();
        MultipartFile file = mock(MultipartFile.class);
        Tracks track = new Tracks();
        track.setId(8L);
        TracksDTO trackDTO = new TracksDTO();
        trackDTO.setId(8L);
        QueueUploadFilesDTO queued = new QueueUploadFilesDTO();
        queued.setId(3L);

        when(file.isEmpty()).thenReturn(false);
        when(repository.findByIdAndDeletedFalse(8L)).thenReturn(Optional.of(track));
        when(mapper.toDto(track)).thenReturn(trackDTO);
        when(queueUploadFilesService.saveStream(any(QueueUploadFilesDTO.class), same(token))).thenReturn(queued);
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        service.uploadFile(8L, file, null, token);

        verifyNoInteractions(sender);
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(sender).send(any(byte[].class));
    }

    @Test
    void defaultsMissingSheetMusicReviewFlagToFalse() {
        TracksRepository repository = mock(TracksRepository.class);
        TracksMapper mapper = mock(TracksMapper.class);
        TracksServiceImpl service = new TracksServiceImpl(
            repository,
            mapper,
            mock(QueueUploadFilesService.class),
            mock(MediaRepository.class),
            mock(InstrumentsRepository.class),
            mock(Sender.class)
        );
        Tracks entity = new Tracks();
        TracksDTO request = new TracksDTO();
        SheetsMusicDTO sheet = new SheetsMusicDTO();
        sheet.setNeedsReview(null);
        request.setScores(Set.of(sheet));
        AtomicReference<Tracks> savedEntity = new AtomicReference<>();

        when(mapper.toEntity(request)).thenReturn(entity);
        when(repository.save(any(Tracks.class))).thenAnswer(invocation -> {
            Tracks saved = invocation.getArgument(0);
            savedEntity.set(saved);
            return saved;
        });
        when(mapper.toDto(any(Tracks.class))).thenReturn(new TracksDTO());

        service.save(request, authentication());

        assertThat(savedEntity.get().getScores()).hasSize(1);
        assertThat(savedEntity.get().getScores().get(0).getNeedsReview()).isFalse();
    }

    @Test
    void deletesExistingScoresBeforeInsertingTheirReplacements() {
        TracksRepository repository = mock(TracksRepository.class);
        TracksMapper mapper = mock(TracksMapper.class);
        TracksServiceImpl service = new TracksServiceImpl(
            repository,
            mapper,
            mock(QueueUploadFilesService.class),
            mock(MediaRepository.class),
            mock(InstrumentsRepository.class),
            mock(Sender.class)
        );
        Tracks entity = new Tracks();
        entity.setId(8L);
        entity.getScores().add(new SheetsMusic());
        TracksDTO request = new TracksDTO();
        request.setId(8L);
        request.setScores(Set.of(new SheetsMusicDTO()));

        when(repository.findByIdAndDeletedFalse(8L)).thenReturn(Optional.of(entity));
        when(repository.save(entity)).thenReturn(entity);
        when(mapper.toDto(entity)).thenReturn(new TracksDTO());
        org.mockito.Mockito.doAnswer(invocation -> {
            assertThat(entity.getScores()).isEmpty();
            return null;
        }).when(repository).flush();

        service.update(8L, request, authentication());

        verify(repository).flush();
        assertThat(entity.getScores()).hasSize(1);
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
