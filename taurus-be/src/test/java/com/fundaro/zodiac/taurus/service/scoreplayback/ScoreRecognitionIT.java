package com.fundaro.zodiac.taurus.service.scoreplayback;

import com.fundaro.zodiac.taurus.IntegrationTest;
import com.fundaro.zodiac.taurus.domain.CommonFieldsOpenSearch;
import com.fundaro.zodiac.taurus.domain.Instruments;
import com.fundaro.zodiac.taurus.domain.Media;
import com.fundaro.zodiac.taurus.domain.SheetsMusic;
import com.fundaro.zodiac.taurus.domain.Tracks;
import com.fundaro.zodiac.taurus.domain.enumeration.MediaAssetStatus;
import com.fundaro.zodiac.taurus.domain.enumeration.StateEnum;
import com.fundaro.zodiac.taurus.domain.scoreplayback.ScoreRecognitionJob;
import com.fundaro.zodiac.taurus.domain.scoreplayback.ScoreRecognitionJobStatus;
import com.fundaro.zodiac.taurus.domain.scoreplayback.ScoreVersion;
import com.fundaro.zodiac.taurus.domain.scoreplayback.ScoreVersionOrigin;
import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaNameResolver;
import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaProvisioningService;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import com.fundaro.zodiac.taurus.repository.scoreplayback.ScoreRecognitionJobRepository;
import com.fundaro.zodiac.taurus.repository.scoreplayback.ScoreVersionRepository;
import com.fundaro.zodiac.taurus.security.AuthoritiesConstants;
import com.fundaro.zodiac.taurus.service.dto.scoreplayback.ScorePlaybackDtos.EngineOutcomeDTO;
import com.fundaro.zodiac.taurus.service.dto.scoreplayback.ScorePlaybackDtos.MusicXmlPartDTO;
import com.fundaro.zodiac.taurus.service.dto.scoreplayback.ScorePlaybackDtos.PartPlaybackDTO;
import com.fundaro.zodiac.taurus.service.dto.scoreplayback.ScorePlaybackDtos.RecognitionJobDTO;
import com.fundaro.zodiac.taurus.service.dto.scoreplayback.ScorePlaybackDtos.ScoreVersionDTO;
import com.fundaro.zodiac.taurus.service.dto.scoreplayback.ScorePlaybackDtos.TrackPlaybackDTO;
import com.fundaro.zodiac.taurus.service.impl.TenantStorageService;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@IntegrationTest
@TestPropertySource(
    properties = {
        "application.base-path=target/score-recognition-it-storage",
        "spring.liquibase.contexts=test",
        "spring.datasource.hikari.maximum-pool-size=4",
        "spring.datasource.hikari.minimum-idle=1",
        "spring.security.oauth2.client.registration.oidc.client-id=test",
        "spring.security.oauth2.client.registration.oidc.client-secret=test",
        "application.score-playback.omr.api-key=it-service-key-0123456789",
        "application.score-playback.kept-versions=3",
    }
)
class ScoreRecognitionIT {

    private static final FakeOmrServer OMR = start();
    private static final String GOOD = """
        <score-partwise version="4.0"><part-list><score-part id="P1"><part-name>Tuba</part-name></score-part></part-list>
        <part id="P1">
          <measure number="1"><attributes><divisions>1</divisions><time><beats>2</beats><beat-type>4</beat-type></time><clef><sign>F</sign><line>4</line></clef></attributes>
            <note><pitch><step>D</step><octave>2</octave></pitch><duration>2</duration></note></measure>
          <measure number="2"><note><pitch><step>A</step><octave>2</octave></pitch><duration>2</duration></note></measure>
        </part></score-partwise>
        """;
    private static final String WRONG_DURATIONS = GOOD.replace("<duration>2</duration></note></measure>\n  <measure number=\"2\">", "<duration>3</duration></note></measure>\n  <measure number=\"2\">");
    private static final String LAYOUT = "[{\"measure\":1,\"page\":1,\"x\":0.1,\"y\":0.2,\"width\":0.3,\"height\":0.05,\"approximate\":false}]";

    @MockBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private ScoreRecognitionQueue queue;

    private final String tenantCode = "score-recognition-it-" + UUID.randomUUID();

    @Autowired
    private TenantSchemaProvisioningService provisioningService;

    @Autowired
    private TenantSchemaNameResolver schemaNameResolver;

    @Autowired
    private TenantTransactionExecutor transactionExecutor;

    @Autowired
    private TenantStorageService storageService;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ScoreRecognitionService recognitionService;

    @Autowired
    private ScoreRecognitionWorker worker;

    @Autowired
    private ScoreRecognitionRecovery recovery;

    @Autowired
    private ScoreVersionCleanup cleanup;

    @Autowired
    private ScoreVersionService versionService;

    @Autowired
    private ScorePlaybackService playbackService;

    @Autowired
    private ScoreRecognitionJobRepository jobRepository;

    @Autowired
    private ScoreVersionRepository versionRepository;

    private Catalog catalog;

    @DynamicPropertySource
    static void omrService(DynamicPropertyRegistry registry) {
        registry.add("application.score-playback.omr.url", OMR::url);
    }

    @AfterAll
    static void stopOmr() {
        OMR.close();
    }

    @BeforeEach
    void provisionTenant() {
        OMR.reset();
        clearInvocations(queue);
        provisioningService.provision(tenantCode);
        catalog = createCatalog();
    }

    @AfterEach
    void dropTenant() throws IOException {
        provisioningService.dropSchema(tenantCode);
        storageService.deleteTenantDirectory(tenantCode);
    }

    @Test
    void recognizesAPartStoresAVersionPerEngineAndProposesTheBetterOne() {
        OMR.answer("/v1/recognitions", 200, "application/json", FakeOmrServer.results(
            FakeOmrServer.engine("AUDIVERIS", "DONE", null, WRONG_DURATIONS, LAYOUT),
            FakeOmrServer.engine("HOMR", "DONE", null, GOOD, null)
        ));

        List<RecognitionJobDTO> started = transactionExecutor.execute(tenantCode, () -> recognitionService.start(catalog.track(), catalog.tuba(), archivist()));

        assertThat(started).singleElement().satisfies(job -> assertThat(job.status()).isEqualTo(ScoreRecognitionJobStatus.QUEUED));
        Long jobId = started.get(0).id();
        verify(queue).publishAfterCommit(tenantCode, jobId);
        assertThatThrownBy(() -> transactionExecutor.execute(tenantCode, () -> recognitionService.start(catalog.track(), catalog.tuba(), archivist())))
            .isInstanceOfSatisfying(RequestAlertException.class, error -> assertThat(error.getErrorKey()).isEqualTo("scorePlayback.recognition.alreadyRunning"));

        worker.run(tenantCode, jobId);
        worker.run(tenantCode, jobId);

        assertThat(OMR.received()).singleElement().satisfies(request -> {
            assertThat(request.path()).isEqualTo("/v1/recognitions");
            assertThat(request.key()).isEqualTo("it-service-key-0123456789");
            assertThat(Long.parseLong(request.contentLength())).isEqualTo(request.bodySize());
        });
        RecognitionJobDTO job = transactionExecutor.execute(tenantCode, () -> recognitionService.latest(catalog.track()).get(0));
        assertThat(job.status()).isEqualTo(ScoreRecognitionJobStatus.DONE);
        assertThat(job.attempts()).isEqualTo(1);
        assertThat(job.engines()).extracting(EngineOutcomeDTO::engine, EngineOutcomeDTO::proposed).containsExactly(
            org.assertj.core.groups.Tuple.tuple("AUDIVERIS", false),
            org.assertj.core.groups.Tuple.tuple("HOMR", true)
        );
        List<ScoreVersionDTO> versions = transactionExecutor.execute(tenantCode, () -> versionService.versions(catalog.track(), catalog.tuba()));
        assertThat(versions).extracting(ScoreVersionDTO::origin, ScoreVersionDTO::current).containsExactly(
            org.assertj.core.groups.Tuple.tuple(ScoreVersionOrigin.OMR_HOMR, true),
            org.assertj.core.groups.Tuple.tuple(ScoreVersionOrigin.OMR_AUDIVERIS, false)
        );
        String layout = transactionExecutor.execute(tenantCode, () -> versionRepository.findById(versions.get(1).id()).map(ScoreVersion::getLayoutJson).orElseThrow());
        assertThat(layout).contains("\"width\":0.3");
        assertThat(notifications()).isEqualTo(1);

        PartPlaybackDTO part = part(transactionExecutor.execute(tenantCode, () -> playbackService.overview(catalog.track(), archivist())), catalog.tuba());
        assertThat(part.hasPages()).isTrue();
        assertThat(part.recognition().status()).isEqualTo(ScoreRecognitionJobStatus.DONE);
        assertThat(part.version().origin()).isEqualTo(ScoreVersionOrigin.OMR_HOMR);
    }

    @Test
    void keepsAnApprovedVersionCurrent() {
        ScoreVersionDTO approved = transactionExecutor.execute(tenantCode, () -> versionService.upload(
            catalog.track(), catalog.tuba(), musicXml("tuba.musicxml", GOOD), null, true, archivist()
        ));
        OMR.answer("/v1/recognitions", 200, "application/json", FakeOmrServer.results(FakeOmrServer.engine("AUDIVERIS", "DONE", null, GOOD, LAYOUT)));

        Long jobId = transactionExecutor.execute(tenantCode, () -> recognitionService.start(catalog.track(), catalog.tuba(), archivist())).get(0).id();
        worker.run(tenantCode, jobId);

        List<ScoreVersionDTO> versions = transactionExecutor.execute(tenantCode, () -> versionService.versions(catalog.track(), catalog.tuba()));
        assertThat(versions).hasSize(2);
        assertThat(versions).filteredOn(ScoreVersionDTO::current).singleElement().satisfies(version -> assertThat(version.id()).isEqualTo(approved.id()));
    }

    @Test
    void failsTheJobWithTheReasonOfTheEnginesOrOfTheService() {
        OMR.answer("/v1/recognitions", 200, "application/json", FakeOmrServer.results(
            FakeOmrServer.engine("AUDIVERIS", "FAILED", "engineTimeout", null, null),
            FakeOmrServer.engine("HOMR", "FAILED", "engineFailed", null, null)
        ));
        Long first = transactionExecutor.execute(tenantCode, () -> recognitionService.start(catalog.track(), catalog.tuba(), archivist())).get(0).id();
        worker.run(tenantCode, first);
        assertThat(job(first)).satisfies(job -> {
            assertThat(job.getStatus()).isEqualTo(ScoreRecognitionJobStatus.FAILED);
            assertThat(job.getErrorCode()).isEqualTo("engineTimeout");
        });

        OMR.answer("/v1/recognitions", 422, "application/json", "{\"code\":\"tooManyPages\"}");
        Long second = transactionExecutor.execute(tenantCode, () -> recognitionService.start(catalog.track(), catalog.tuba(), archivist())).get(0).id();
        worker.run(tenantCode, second);
        assertThat(job(second).getErrorCode()).isEqualTo("tooManyPages");

        OMR.answer("/v1/recognitions", 503, "application/json", "{\"code\":\"busy\"}");
        Long third = transactionExecutor.execute(tenantCode, () -> recognitionService.start(catalog.track(), catalog.tuba(), archivist())).get(0).id();
        worker.run(tenantCode, third);
        assertThat(job(third).getErrorCode()).isEqualTo("serviceUnavailable");
        assertThat(notifications()).isEqualTo(3);
    }

    @Test
    void recognizesAllThePartsWithPagesAndNotifiesOnceAtTheEnd() {
        OMR.answer("/v1/recognitions", 200, "application/json", FakeOmrServer.results(FakeOmrServer.engine("HOMR", "DONE", null, GOOD, null)));

        List<RecognitionJobDTO> started = transactionExecutor.execute(tenantCode, () -> recognitionService.start(catalog.track(), null, archivist()));

        assertThat(started).extracting(RecognitionJobDTO::scoreId).containsExactlyInAnyOrder(catalog.tuba(), catalog.clarinet());
        UUID batch = job(started.get(0).id()).getBatchId();
        assertThat(batch).isNotNull().isEqualTo(job(started.get(1).id()).getBatchId());
        worker.run(tenantCode, started.get(0).id());
        assertThat(notifications()).isZero();
        worker.run(tenantCode, started.get(1).id());
        assertThat(notifications()).isEqualTo(1);

        assertThatThrownBy(() -> transactionExecutor.execute(tenantCode, () -> recognitionService.start(catalog.track(), catalog.empty(), archivist())))
            .isInstanceOfSatisfying(RequestAlertException.class, error -> assertThat(error.getErrorKey()).isEqualTo("scorePlayback.recognition.noPages"));
    }

    @Test
    void queuesInterruptedJobsAgainUntilTheLastAttempt() {
        Long jobId = transactionExecutor.execute(tenantCode, () -> recognitionService.start(catalog.track(), catalog.tuba(), archivist())).get(0).id();
        transactionExecutor.execute(tenantCode, () -> jobRepository.claim(jobId, ZonedDateTime.now(), "test"));

        recovery.recover(tenantCode, ZonedDateTime.now().plusHours(1));

        assertThat(job(jobId).getStatus()).isEqualTo(ScoreRecognitionJobStatus.QUEUED);
        verify(queue, times(2)).publishAfterCommit(eq(tenantCode), eq(jobId));

        transactionExecutor.execute(tenantCode, () -> {
            ScoreRecognitionJob job = jobRepository.findById(jobId).orElseThrow();
            job.setStatus(ScoreRecognitionJobStatus.RUNNING);
            job.setStartedAt(ZonedDateTime.now());
            job.setAttempts(3);
        });
        recovery.recover(tenantCode, ZonedDateTime.now().plusHours(1));

        assertThat(job(jobId)).satisfies(job -> {
            assertThat(job.getStatus()).isEqualTo(ScoreRecognitionJobStatus.FAILED);
            assertThat(job.getErrorCode()).isEqualTo("interrupted");
        });
        worker.run(tenantCode, jobId);
        assertThat(OMR.received()).isEmpty();
    }

    @Test
    void convertsMidiUploadsWithTheOmrService() {
        OMR.answer("/v1/midi-conversions", 200, "application/vnd.recordare.musicxml+xml", MusicXmlDocumentsTest.TWO_PARTS);

        List<MusicXmlPartDTO> parts = transactionExecutor.execute(tenantCode, () -> versionService.inspect(catalog.track(), catalog.tuba(), midi()));
        ScoreVersionDTO version = transactionExecutor.execute(tenantCode, () -> versionService.upload(catalog.track(), catalog.tuba(), midi(), "P2", false, archivist()));

        assertThat(parts).extracting(MusicXmlPartDTO::name).containsExactly("Tuba", "Clarinetto");
        assertThat(version.origin()).isEqualTo(ScoreVersionOrigin.UPLOAD_MIDI);
        assertThat(version.sourcePartName()).isEqualTo("Clarinetto");
        assertThat(OMR.received()).extracting(FakeOmrServer.Received::path).containsOnly("/v1/midi-conversions");
    }

    @Test
    void removesTheOldVersionsThatAreNeitherCurrentNorApproved() {
        List<ScoreVersionDTO> uploaded = new java.util.ArrayList<>();
        for (int index = 0; index < 6; index++) {
            boolean approve = index == 0;
            uploaded.add(transactionExecutor.execute(tenantCode, () -> versionService.upload(catalog.track(), catalog.tuba(), musicXml("tuba.musicxml", GOOD), null, approve, archivist())));
        }

        int removed = cleanup.cleanup(tenantCode);

        assertThat(removed).isEqualTo(2);
        List<ScoreVersionDTO> kept = transactionExecutor.execute(tenantCode, () -> versionService.versions(catalog.track(), catalog.tuba()));
        assertThat(kept).extracting(ScoreVersionDTO::versionNumber).containsExactly(6, 5, 4, 1);
        Long removedMedia = transactionExecutor.execute(tenantCode, () -> versionRepository.findById(uploaded.get(1).id()).map(ScoreVersion::getMediaAssetId).orElseThrow());
        assertThat(jdbcTemplate.queryForObject("SELECT deleted FROM " + schema() + ".media_asset WHERE id = ?", Boolean.class, removedMedia)).isTrue();
        assertThat(cleanup.cleanup(tenantCode)).isZero();
    }

    private Catalog createCatalog() {
        return transactionExecutor.execute(tenantCode, () -> {
            Instruments tuba = entityManager.createQuery("select i from Instruments i where i.name = 'Basso 1°'", Instruments.class).getSingleResult();
            Tracks track = audited(new Tracks(), "Marcia del riconoscimento");
            track.setState(StateEnum.COMPLETE);
            SheetsMusic tubaPart = part(tuba, page("tuba-page"));
            SheetsMusic clarinetPart = part(tuba, page("clarinet-page"));
            SheetsMusic emptyPart = new SheetsMusic();
            track.getScores().addAll(List.of(tubaPart, clarinetPart, emptyPart));
            entityManager.persist(track);
            entityManager.flush();
            return new Catalog(track.getId(), tubaPart.getId(), clarinetPart.getId(), emptyPart.getId());
        });
    }

    private Media page(String name) {
        byte[] content = (name + UUID.randomUUID()).getBytes(StandardCharsets.UTF_8);
        String sha256;
        try {
            sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
        Media media = audited(new Media(), name);
        media.setStorageKey("scores/" + UUID.randomUUID() + "/" + sha256 + ".png");
        media.setOriginalFilename(name + ".png");
        media.setMimeType("image/png");
        media.setFileExtension("png");
        media.setFileSize(content.length);
        media.setSha256(sha256);
        media.setStatus(MediaAssetStatus.READY);
        try {
            storageService.writeAtomically(tenantCode, media.getStorageKey(), content);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        entityManager.persist(media);
        return media;
    }

    private static SheetsMusic part(Instruments instrument, Media page) {
        SheetsMusic part = new SheetsMusic();
        part.getInstruments().add(instrument);
        part.getMedia().add(page);
        return part;
    }

    private <T extends CommonFieldsOpenSearch> T audited(T entity, String name) {
        Date now = new Date();
        entity.setName(name);
        entity.setDeleted(false);
        entity.setInsertBy("test");
        entity.setInsertDate(now);
        entity.setEditBy("test");
        entity.setEditDate(now);
        return entity;
    }

    private ScoreRecognitionJob job(Long id) {
        return transactionExecutor.execute(tenantCode, () -> jobRepository.findById(id).orElseThrow());
    }

    private long notifications() {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + schema() + ".notification_outbox WHERE aggregate_type = 'SCORE_RECOGNITION'",
            Long.class
        );
    }

    private static PartPlaybackDTO part(TrackPlaybackDTO playback, Long scoreId) {
        return playback.parts().stream().filter(part -> part.scoreId().equals(scoreId)).findFirst().orElseThrow();
    }

    private static MockMultipartFile musicXml(String name, String content) {
        return new MockMultipartFile("file", name, "application/xml", content.getBytes(StandardCharsets.UTF_8));
    }

    private static MockMultipartFile midi() {
        return new MockMultipartFile("file", "brano.mid", "audio/midi", "MThd".getBytes(StandardCharsets.US_ASCII));
    }

    private JwtAuthenticationToken archivist() {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("recognition-archivist").claim("tenant", tenantCode)
            .issuedAt(now).expiresAt(now.plusSeconds(300)).build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(AuthoritiesConstants.ARCHIVIST)));
    }

    private String schema() {
        return '"' + schemaNameResolver.resolve(tenantCode).replace("\"", "\"\"") + '"';
    }

    private static FakeOmrServer start() {
        try {
            return new FakeOmrServer();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private record Catalog(Long track, Long tuba, Long clarinet, Long empty) {}
}
