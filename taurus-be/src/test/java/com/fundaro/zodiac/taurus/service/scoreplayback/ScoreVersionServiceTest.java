package com.fundaro.zodiac.taurus.service.scoreplayback;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.SheetsMusic;
import com.fundaro.zodiac.taurus.domain.Tracks;
import com.fundaro.zodiac.taurus.domain.scoreplayback.ScoreVersion;
import com.fundaro.zodiac.taurus.domain.scoreplayback.ScoreVersionOrigin;
import com.fundaro.zodiac.taurus.domain.scoreplayback.ScoreVersionStatus;
import com.fundaro.zodiac.taurus.repository.TracksRepository;
import com.fundaro.zodiac.taurus.repository.scoreplayback.ScoreVersionRepository;
import com.fundaro.zodiac.taurus.service.MediaService;
import com.fundaro.zodiac.taurus.service.dto.MediaDTO;
import com.fundaro.zodiac.taurus.service.dto.scoreplayback.ScorePlaybackDtos.CorrectionDTO;
import com.fundaro.zodiac.taurus.service.dto.scoreplayback.ScorePlaybackDtos.MeasureBoxDTO;
import com.fundaro.zodiac.taurus.service.dto.scoreplayback.ScorePlaybackDtos.MusicXmlPartDTO;
import com.fundaro.zodiac.taurus.service.scoreplayback.ScorePlaybackSupport.Viewer;
import com.fundaro.zodiac.taurus.service.scoreplayback.omr.OmrClient;
import com.fundaro.zodiac.taurus.service.scoreplayback.omr.OmrException;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScoreVersionServiceTest {

    private final ScorePlaybackSupport support = mock(ScorePlaybackSupport.class);
    private final ScoreVersionRepository versionRepository = mock(ScoreVersionRepository.class);
    private final MediaService mediaService = mock(MediaService.class);
    private final OmrClient omrClient = mock(OmrClient.class);
    private final ScoreErrorReportService reportService = mock(ScoreErrorReportService.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final SheetsMusic part = new SheetsMusic();
    private ScoreVersionService service;

    @BeforeEach
    void setUp() {
        Tracks track = new Tracks();
        part.setId(9L);
        MediaDTO media = new MediaDTO();
        media.setId(5L);
        when(support.requireTrack(4L, Viewer.MANAGER)).thenReturn(track);
        when(support.requirePart(track, 9L)).thenReturn(part);
        when(mediaService.store(any(), anyString(), anyString(), eq(ScoreVersionService.MEDIA_CATEGORY), any(JwtAuthenticationToken.class))).thenReturn(media);
        when(versionRepository.findMaxVersionNumber(9L)).thenReturn(1);
        service = new ScoreVersionService(
            support,
            versionRepository,
            mock(TracksRepository.class),
            mediaService,
            new ApplicationProperties(),
            new ScoreVersionWriter(support, versionRepository, new ObjectMapper()),
            omrClient,
            new ScorePlaybackMetrics(registry),
            reportService
        );
    }

    @Test
    void refusesAnUploadThatTookTheVersionNumberOfAConcurrentOne() {
        when(versionRepository.saveAndFlush(any(ScoreVersion.class))).thenThrow(new DataIntegrityViolationException("ux_score_version_number"));

        assertThatThrownBy(() -> service.upload(4L, 9L, file("tuba.musicxml", MusicXmlDocumentsTest.TWO_PARTS), "P1", false, archivist()))
            .isInstanceOfSatisfying(RequestAlertException.class, exception -> {
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                assertThat(exception.getErrorKey()).isEqualTo("scorePlayback.version.conflict");
            });
        assertThat(part.getCurrentScoreVersionId()).isNull();
    }

    @Test
    void acceptsMidiFilesOnlyWhenTheOmrServiceConvertsThem() {
        assertThatThrownBy(() -> service.inspect(4L, 9L, file("brano.mid", "MThd")))
            .isInstanceOfSatisfying(RequestAlertException.class, exception -> assertThat(exception.getErrorKey()).isEqualTo("scorePlayback.upload.unsupportedFormat"));

        when(omrClient.isConfigured()).thenReturn(true);
        when(omrClient.convertMidi(eq("brano.mid"), any())).thenReturn(MusicXmlDocumentsTest.TWO_PARTS.getBytes(StandardCharsets.UTF_8));

        List<MusicXmlPartDTO> parts = service.inspect(4L, 9L, file("brano.mid", "MThd"));

        assertThat(parts).extracting(MusicXmlPartDTO::id).containsExactly("P1", "P2");
    }

    @Test
    void reportsUnreadableMidiFilesAndAnUnavailableService() {
        when(omrClient.isConfigured()).thenReturn(true);
        when(omrClient.convertMidi(eq("rotto.mid"), any())).thenThrow(OmrException.rejected("unreadableMidi"));
        when(omrClient.convertMidi(eq("brano.midi"), any())).thenThrow(OmrException.unavailable("down"));

        assertThatThrownBy(() -> service.upload(4L, 9L, file("rotto.mid", "MThd"), null, false, archivist()))
            .isInstanceOfSatisfying(RequestAlertException.class, exception -> {
                assertThat(exception.getErrorKey()).isEqualTo("scorePlayback.upload.invalid");
                assertThat(exception.getArgs().values()).containsEntry("reason", "unreadableMidi");
            });
        assertThatThrownBy(() -> service.inspect(4L, 9L, file("brano.midi", "MThd")))
            .isInstanceOfSatisfying(RequestAlertException.class, exception -> {
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                assertThat(exception.getErrorKey()).isEqualTo("scorePlayback.omr.unavailable");
            });
        assertThat(registry.counter("taurus.score.upload", "format", "midi", "outcome", "rejected").count()).isEqualTo(1);
    }

    @Test
    void storesACorrectionDerivedFromItsParentAndClosesTheReportsWhenApproved() {
        ScoreVersion parent = parent(3);
        when(versionRepository.findByIdAndSheetMusicIdAndDeletedFalse(7L, 9L)).thenReturn(Optional.of(parent));

        service.correct(4L, 9L, new CorrectionDTO(7L, tuba(), true, null), archivist());

        ArgumentCaptor<ScoreVersion> saved = ArgumentCaptor.forClass(ScoreVersion.class);
        verify(versionRepository).saveAndFlush(saved.capture());
        assertThat(saved.getValue()).satisfies(version -> {
            assertThat(version.getOrigin()).isEqualTo(ScoreVersionOrigin.CORRECTION);
            assertThat(version.getParentVersionId()).isEqualTo(7L);
            assertThat(version.getPagesFingerprint()).isEqualTo("parent-pages");
            assertThat(version.getSourcePartName()).isEqualTo("Tuba");
            assertThat(version.getStatus()).isEqualTo(ScoreVersionStatus.APPROVED);
            assertThat(version.getMeasureCount()).isEqualTo(3);
            assertThat(version.getLayoutJson()).contains("\"measure\":3");
        });
        verify(reportService).closeForApproval(eq(part), any(), eq("archivist"));
        assertThat(registry.counter("taurus.score.upload", "format", "correction", "outcome", "stored").count()).isEqualTo(1);
    }

    @Test
    void refusesCorrectionsWithSeveralPartsOrUnknownMeasures() {
        ScoreVersion parent = parent(3);
        when(versionRepository.findByIdAndSheetMusicIdAndDeletedFalse(7L, 9L)).thenReturn(Optional.of(parent));

        assertThatThrownBy(() -> service.correct(4L, 9L, new CorrectionDTO(7L, MusicXmlDocumentsTest.TWO_PARTS, false, null), archivist()))
            .isInstanceOfSatisfying(RequestAlertException.class, exception -> {
                assertThat(exception.getErrorKey()).isEqualTo("scorePlayback.correction.invalid");
                assertThat(exception.getArgs().values()).containsEntry("reason", "severalParts");
            });
        assertThatThrownBy(() -> service.correct(4L, 9L, new CorrectionDTO(7L, tuba(), false, Arrays.asList(0, 1, 3)), archivist()))
            .isInstanceOfSatisfying(RequestAlertException.class, exception -> assertThat(exception.getArgs().values()).containsEntry("reason", "measureSources"));
        assertThatThrownBy(() -> service.correct(4L, 9L, new CorrectionDTO(8L, tuba(), false, null), archivist()))
            .isInstanceOfSatisfying(RequestAlertException.class, exception -> assertThat(exception.getErrorKey()).isEqualTo("scorePlayback.version.notFound"));
        verify(reportService, never()).closeForApproval(any(), any(), anyString());
        assertThat(registry.counter("taurus.score.upload", "format", "correction", "outcome", "rejected").count()).isEqualTo(2);
    }

    @Test
    void theLayoutOfACorrectionFollowsTheMeasuresItComesFrom() {
        List<MeasureBoxDTO> parentLayout = List.of(box(1, 1), box(2, 1), box(3, 2));

        List<MeasureBoxDTO> moved = ScoreVersionService.correctionLayout(parentLayout, 3, 3, Arrays.asList(0, null, 2));

        assertThat(moved).extracting(MeasureBoxDTO::measure, MeasureBoxDTO::page).containsExactly(tuple(1, 1), tuple(3, 2));
        assertThat(ScoreVersionService.correctionLayout(parentLayout, 3, 3, null)).isEqualTo(parentLayout);
        assertThat(ScoreVersionService.correctionLayout(parentLayout, 3, 4, null)).isNull();
        assertThat(ScoreVersionService.correctionLayout(null, 3, 2, List.of(0, 2))).isNull();
    }

    private ScoreVersion parent(int measures) {
        ScoreVersion parent = new ScoreVersion();
        parent.setId(7L);
        parent.setSheetMusicId(9L);
        parent.setMeasureCount(measures);
        parent.setPagesFingerprint("parent-pages");
        parent.setSourcePartName("Tuba");
        parent.setLayoutJson("[{\"measure\":3,\"page\":1,\"x\":0.1,\"y\":0.2,\"width\":0.3,\"height\":0.1,\"approximate\":false}]");
        when(support.layout(parent)).thenReturn(List.of(box(3, 1)));
        return parent;
    }

    private static MeasureBoxDTO box(int measure, int page) {
        return new MeasureBoxDTO(measure, page, 0.1, 0.2, 0.3, 0.1, false);
    }

    private static String tuba() {
        return new String(MusicXmlDocuments.toXml(MusicXmlDocuments.singlePart(MusicXmlDocuments.read(MusicXmlDocumentsTest.TWO_PARTS.getBytes(StandardCharsets.UTF_8)), "P1")), StandardCharsets.UTF_8);
    }

    private static MockMultipartFile file(String name, String content) {
        return new MockMultipartFile("file", name, "application/octet-stream", content.getBytes(StandardCharsets.UTF_8));
    }

    private static JwtAuthenticationToken archivist() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("archivist").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).build();
        return new JwtAuthenticationToken(jwt);
    }
}
