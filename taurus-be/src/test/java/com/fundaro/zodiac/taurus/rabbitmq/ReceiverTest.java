package com.fundaro.zodiac.taurus.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fundaro.zodiac.taurus.multitenancy.TenantContext;
import com.fundaro.zodiac.taurus.domain.enumeration.UploadFileStatusEnum;
import com.fundaro.zodiac.taurus.service.QueueUploadFilesService;
import com.fundaro.zodiac.taurus.service.MediaService;
import com.fundaro.zodiac.taurus.service.TracksService;
import com.fundaro.zodiac.taurus.service.impl.PdfProcessingService;
import com.fundaro.zodiac.taurus.service.impl.ImageTransformationService;
import com.fundaro.zodiac.taurus.service.impl.TenantStorageService;
import com.fundaro.zodiac.taurus.service.dto.QueueUploadFilesDTO;
import com.fundaro.zodiac.taurus.service.dto.SheetsMusicDTO;
import com.fundaro.zodiac.taurus.service.dto.TracksDTO;
import com.fundaro.zodiac.taurus.service.upload.UploadJobMetrics;
import com.fundaro.zodiac.taurus.utils.Converter;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class ReceiverTest {

    private final QueueUploadFilesService queueUploadFilesService = mock(QueueUploadFilesService.class);
    private final TracksService tracksService = mock(TracksService.class);
    private final PdfProcessingService pdfProcessingService = mock(PdfProcessingService.class);
    private final TenantStorageService tenantStorageService = mock(TenantStorageService.class);
    private final MediaService mediaService = mock(MediaService.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private final Receiver receiver = new Receiver(
        queueUploadFilesService,
        tracksService,
        pdfProcessingService,
        tenantStorageService,
        mediaService,
        new ImageTransformationService(),
        new UploadJobMetrics(registry)
    );

    @Test
    void activatesMessageTenantBeforeLoadingUploadJobAndRestoresContextAfterwards() throws Exception {
        JwtAuthenticationToken token = authentication();
        when(queueUploadFilesService.findOne(3L, token)).thenAnswer(invocation -> {
            assertThat(TenantContext.getTenantCode()).contains("BMCDG");
            return Optional.empty();
        });

        receiver.receive(Converter.objectToBytes(new UploadFilesPackage(3L, token)));

        assertThat(TenantContext.getTenantCode()).isEmpty();
        assertThat(failures("MISSING_JOB")).isEqualTo(1);
    }

    @Test
    void persistsInProgressAndErrorWhenTheTrackCannotBeProcessed() throws Exception {
        JwtAuthenticationToken token = authentication();
        when(queueUploadFilesService.findOne(3L, token)).thenReturn(Optional.of(job()));
        when(queueUploadFilesService.transitionStatus(3L, UploadFileStatusEnum.TO_PROCESS, UploadFileStatusEnum.IN_PROGRESS, token)).thenReturn(true);
        when(tracksService.findOne(8L, token)).thenReturn(Optional.empty());

        receiver.receive(Converter.objectToBytes(new UploadFilesPackage(3L, token)));

        verify(queueUploadFilesService).transitionStatus(3L, UploadFileStatusEnum.TO_PROCESS, UploadFileStatusEnum.IN_PROGRESS, token);
        verify(queueUploadFilesService).transitionStatus(3L, UploadFileStatusEnum.IN_PROGRESS, UploadFileStatusEnum.ERROR, token);
        assertThat(operations("FAILED")).isEqualTo(1);
        assertThat(failures("PROCESSING_FAILED")).isEqualTo(1);
    }

    @Test
    void ignoresARedeliveredJobThatIsNoLongerQueued() throws Exception {
        JwtAuthenticationToken token = authentication();
        QueueUploadFilesDTO upload = job();
        upload.setStatus(UploadFileStatusEnum.DONE);
        when(queueUploadFilesService.findOne(3L, token)).thenReturn(Optional.of(upload));
        when(queueUploadFilesService.transitionStatus(3L, UploadFileStatusEnum.TO_PROCESS, UploadFileStatusEnum.IN_PROGRESS, token)).thenReturn(false);

        receiver.receive(Converter.objectToBytes(new UploadFilesPackage(3L, token)));

        verifyNoInteractions(tracksService);
        assertThat(operations("IGNORED")).isEqualTo(1);
    }

    /**
     * Il percorso completo del worker su un PDF vero: una pagina generata qui viene convertita in
     * immagine, le parti sono costruite dal servizio e il job arriva a {@code DONE}. La conversione
     * non è finta, perché è l'unico punto in cui si vede che un PDF valido produce una pagina.
     */
    @Test
    void takesTheJobFromQueuedToDone() throws Exception {
        JwtAuthenticationToken token = authentication();
        Path workingDirectory = Files.createTempDirectory("receiver-test");
        try {
            QueueUploadFilesDTO upload = job();
            TracksDTO track = new TracksDTO();
            track.setId(8L);
            when(queueUploadFilesService.findOne(3L, token)).thenReturn(Optional.of(upload));
            when(queueUploadFilesService.transitionStatus(3L, UploadFileStatusEnum.TO_PROCESS, UploadFileStatusEnum.IN_PROGRESS, token)).thenReturn(true);
            when(queueUploadFilesService.transitionStatus(3L, UploadFileStatusEnum.IN_PROGRESS, UploadFileStatusEnum.DONE, token)).thenReturn(true);
            when(tracksService.findOne(8L, token)).thenReturn(Optional.of(track));
            when(mediaService.getContent(any(Long.class), any(AbstractAuthenticationToken.class))).thenReturn(new MediaService.MediaContent("score.pdf", "application/pdf", 1024, onePagePdf()));
            when(tenantStorageService.createTemporaryDirectory(anyString(), anyString())).thenReturn(workingDirectory);
            when(pdfProcessingService.buildSheets(any(), anyList(), any(), any())).thenReturn(List.of(new SheetsMusicDTO()));

            receiver.receive(Converter.objectToBytes(new UploadFilesPackage(3L, token)));

            verify(tracksService).appendScores(anyLong(), anyList(), any());
            verify(queueUploadFilesService).transitionStatus(3L, UploadFileStatusEnum.IN_PROGRESS, UploadFileStatusEnum.DONE, token);
            verify(queueUploadFilesService, never()).transitionStatus(3L, UploadFileStatusEnum.IN_PROGRESS, UploadFileStatusEnum.ERROR, token);
            assertThat(operations("COMPLETED")).isEqualTo(1);
            assertThat(registry.find("taurus.upload.job.duration").tag("outcome", "COMPLETED").timer().count()).isEqualTo(1);
        } finally {
            deleteRecursively(workingDirectory);
        }
    }

    /**
     * Il recupero ha chiuso il job credendo il worker fermo, ma il worker stava ancora lavorando:
     * le parti sono state create e il job non arriva a {@code DONE}. Va contato, perché un nuovo
     * tentativo dell'utente duplicherebbe le parti.
     */
    @Test
    void countsAnElaborationThatEndsAfterTheJobWasClosed() throws Exception {
        JwtAuthenticationToken token = authentication();
        Path workingDirectory = Files.createTempDirectory("receiver-test");
        try {
            TracksDTO track = new TracksDTO();
            track.setId(8L);
            when(queueUploadFilesService.findOne(3L, token)).thenReturn(Optional.of(job()));
            when(queueUploadFilesService.transitionStatus(3L, UploadFileStatusEnum.TO_PROCESS, UploadFileStatusEnum.IN_PROGRESS, token)).thenReturn(true);
            when(queueUploadFilesService.transitionStatus(3L, UploadFileStatusEnum.IN_PROGRESS, UploadFileStatusEnum.DONE, token)).thenReturn(false);
            when(tracksService.findOne(8L, token)).thenReturn(Optional.of(track));
            when(mediaService.getContent(any(Long.class), any(AbstractAuthenticationToken.class))).thenReturn(new MediaService.MediaContent("score.pdf", "application/pdf", 1024, onePagePdf()));
            when(tenantStorageService.createTemporaryDirectory(anyString(), anyString())).thenReturn(workingDirectory);
            when(pdfProcessingService.buildSheets(any(), anyList(), any(), any())).thenReturn(List.of(new SheetsMusicDTO()));

            receiver.receive(Converter.objectToBytes(new UploadFilesPackage(3L, token)));

            assertThat(operations("LATE")).isEqualTo(1);
            assertThat(operations("COMPLETED")).isZero();
        } finally {
            deleteRecursively(workingDirectory);
        }
    }

    @Test
    void refusesAMessageWithoutATenant() throws Exception {
        Instant now = Instant.now();
        Jwt withoutTenant = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject("user-1")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .build();
        byte[] message = Converter.objectToBytes(new UploadFilesPackage(3L, new JwtAuthenticationToken(withoutTenant)));

        assertThatThrownBy(() -> receiver.receive(message)).isInstanceOf(IllegalArgumentException.class);

        assertThat(failures("MISSING_TENANT")).isEqualTo(1);
        verifyNoInteractions(queueUploadFilesService);
    }

    private QueueUploadFilesDTO job() {
        QueueUploadFilesDTO upload = new QueueUploadFilesDTO();
        upload.setId(3L);
        upload.setTrackId(8L);
        upload.setSourceMediaAssetId(11L);
        return upload;
    }

    private double operations(String operation) {
        return registry.counter("taurus.upload.jobs", "operation", operation).count();
    }

    private double failures(String reason) {
        return registry.counter("taurus.upload.failures", "reason", reason).count();
    }

    /** Un PDF minimo ma valido: una pagina vuota, abbastanza per una conversione vera. */
    private static byte[] onePagePdf() throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(bytes);
            return bytes.toByteArray();
        }
    }

    private static void deleteRecursively(Path directory) throws IOException {
        if (!Files.exists(directory)) return;
        try (var paths = Files.walk(directory)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Una directory temporanea non cancellata non fa fallire la prova.
                }
            });
        }
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
