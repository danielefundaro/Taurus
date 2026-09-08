package com.fundaro.zodiac.taurus.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fundaro.zodiac.taurus.multitenancy.TenantContext;
import com.fundaro.zodiac.taurus.domain.enumeration.UploadFileStatusEnum;
import com.fundaro.zodiac.taurus.service.QueueUploadFilesService;
import com.fundaro.zodiac.taurus.service.MediaService;
import com.fundaro.zodiac.taurus.service.TracksService;
import com.fundaro.zodiac.taurus.service.impl.PdfProcessingService;
import com.fundaro.zodiac.taurus.service.impl.TenantStorageService;
import com.fundaro.zodiac.taurus.service.dto.QueueUploadFilesDTO;
import com.fundaro.zodiac.taurus.utils.Converter;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class ReceiverTest {

    @Test
    void activatesMessageTenantBeforeLoadingUploadJobAndRestoresContextAfterwards() throws Exception {
        QueueUploadFilesService queueUploadFilesService = mock(QueueUploadFilesService.class);
        TracksService tracksService = mock(TracksService.class);
        Receiver receiver = new Receiver(
            queueUploadFilesService,
            tracksService,
            mock(PdfProcessingService.class),
            mock(TenantStorageService.class),
            mock(MediaService.class)
        );
        JwtAuthenticationToken token = authentication();
        when(queueUploadFilesService.findOne(3L, token)).thenAnswer(invocation -> {
            assertThat(TenantContext.getTenantCode()).contains("BMCDG");
            return Optional.empty();
        });

        receiver.receive(Converter.objectToBytes(new UploadFilesPackage(3L, token)));

        assertThat(TenantContext.getTenantCode()).isEmpty();
    }

    @Test
    void persistsInProgressAndErrorWhenTheTrackCannotBeProcessed() throws Exception {
        QueueUploadFilesService queueUploadFilesService = mock(QueueUploadFilesService.class);
        TracksService tracksService = mock(TracksService.class);
        Receiver receiver = new Receiver(
            queueUploadFilesService,
            tracksService,
            mock(PdfProcessingService.class),
            mock(TenantStorageService.class),
            mock(MediaService.class)
        );
        JwtAuthenticationToken token = authentication();
        QueueUploadFilesDTO upload = new QueueUploadFilesDTO();
        upload.setId(3L);
        upload.setTrackId(8L);
        when(queueUploadFilesService.findOne(3L, token)).thenReturn(Optional.of(upload));
        when(queueUploadFilesService.transitionStatus(3L, UploadFileStatusEnum.TO_PROCESS, UploadFileStatusEnum.IN_PROGRESS, token)).thenReturn(true);
        when(tracksService.findOne(8L, token)).thenReturn(Optional.empty());

        receiver.receive(Converter.objectToBytes(new UploadFilesPackage(3L, token)));

        verify(queueUploadFilesService).transitionStatus(3L, UploadFileStatusEnum.TO_PROCESS, UploadFileStatusEnum.IN_PROGRESS, token);
        verify(queueUploadFilesService).transitionStatus(3L, UploadFileStatusEnum.IN_PROGRESS, UploadFileStatusEnum.ERROR, token);
    }

    @Test
    void ignoresARedeliveredJobThatIsNoLongerQueued() throws Exception {
        QueueUploadFilesService queueUploadFilesService = mock(QueueUploadFilesService.class);
        TracksService tracksService = mock(TracksService.class);
        Receiver receiver = new Receiver(
            queueUploadFilesService,
            tracksService,
            mock(PdfProcessingService.class),
            mock(TenantStorageService.class),
            mock(MediaService.class)
        );
        JwtAuthenticationToken token = authentication();
        QueueUploadFilesDTO upload = new QueueUploadFilesDTO();
        upload.setId(3L);
        upload.setTrackId(8L);
        upload.setStatus(UploadFileStatusEnum.DONE);
        when(queueUploadFilesService.findOne(3L, token)).thenReturn(Optional.of(upload));
        when(queueUploadFilesService.transitionStatus(3L, UploadFileStatusEnum.TO_PROCESS, UploadFileStatusEnum.IN_PROGRESS, token)).thenReturn(false);

        receiver.receive(Converter.objectToBytes(new UploadFilesPackage(3L, token)));

        verifyNoInteractions(tracksService);
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
