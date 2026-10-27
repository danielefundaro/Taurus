package com.fundaro.zodiac.taurus.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.Media;
import com.fundaro.zodiac.taurus.domain.SheetsMusic;
import com.fundaro.zodiac.taurus.domain.TrackPageEditReceipt;
import com.fundaro.zodiac.taurus.domain.Tracks;
import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaNameResolver;
import com.fundaro.zodiac.taurus.repository.MediaRepository;
import com.fundaro.zodiac.taurus.repository.TrackPageEditReceiptRepository;
import com.fundaro.zodiac.taurus.repository.TracksRepository;
import com.fundaro.zodiac.taurus.service.MediaService;
import com.fundaro.zodiac.taurus.service.dto.ChildrenEntitiesDTO;
import com.fundaro.zodiac.taurus.service.dto.MediaDTO;
import com.fundaro.zodiac.taurus.service.dto.TrackPageImageDTOs;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * L'orchestrazione del salvataggio di una pagina modificata: che cosa viene scritto, che cosa viene
 * rigiocato, che cosa viene rifiutato e che cosa finisce nelle metriche.
 *
 * <p>Le prove girano senza Spring: il renderer è reale sulle immagini piccole, perché la pipeline ha
 * i suoi test, e diventa un doppio solo quando serve un rendering lento o un errore.
 */
class TrackPageImageServiceTest {

    private static final UUID KEY = UUID.fromString("b5b0ec70-0f1d-4e1a-b6c7-d8975b20fb6b");

    private TracksRepository tracks;
    private MediaRepository media;
    private TrackPageEditReceiptRepository receipts;
    private MediaService mediaService;
    private SimpleMeterRegistry registry;
    private ApplicationProperties properties;
    private JwtAuthenticationToken token;

    @BeforeEach
    void prepareCollaborators() {
        tracks = mock(TracksRepository.class);
        media = mock(MediaRepository.class);
        receipts = mock(TrackPageEditReceiptRepository.class);
        mediaService = mock(MediaService.class);
        registry = new SimpleMeterRegistry();
        properties = new ApplicationProperties();
        token = authentication("ROLE_ARCHIVIST");
        when(receipts.findByRequestedByAndRequestKey(any(), any())).thenReturn(Optional.empty());
    }

    @Test
    void replacesTheRequestedPageWithEveryCropResult() throws Exception {
        TrackPageImageService service = service(new ImageTransformationService());
        Fixture fixture = fixture();

        TrackPageImageDTOs.EditResult result = service.edit(4L, 5L, 6L, KEY, twoCrops(2L), token);

        assertThat(result.trackVersion()).isEqualTo(3L);
        assertThat(result.media()).extracting(ChildrenEntitiesDTO::getIndex).containsExactly(8L, 9L);
        assertThat(result.media()).extracting(ChildrenEntitiesDTO::getOrder).containsExactly(1L, 2L);
        assertThat(fixture.score.getMedia()).containsExactly(fixture.firstNew, fixture.secondNew, fixture.following);
        verify(tracks).flush();
        verify(mediaService).deleteIfUnreferenced(6L, token);
        verify(receipts).saveAndFlush(any());
        assertThat(counter("taurus.image.edit", "outcome", "done")).isEqualTo(1);
        assertThat(counter("taurus.image.operations", "operation", "crop")).isEqualTo(1);
        assertThat(counter("taurus.image.storage.compensations", null, null)).isZero();
    }

    /** Una risposta persa e ritentata con la stessa chiave non deve produrre una seconda modifica. */
    @Test
    void replaysTheReceiptWithoutRenderingAgain() throws Exception {
        ImageTransformationService renderer = mock(ImageTransformationService.class);
        TrackPageImageService service = service(renderer);
        Fixture fixture = fixture();
        fixture.score.getMedia().clear();
        fixture.score.getMedia().addAll(List.of(fixture.firstNew, fixture.secondNew, fixture.following));
        when(receipts.findByRequestedByAndRequestKey("user-1", KEY)).thenReturn(Optional.of(receipt(fixture.track.getEntityVersion(), service, 2L)));

        TrackPageImageDTOs.EditResult result = service.edit(4L, 5L, 6L, KEY, twoCrops(2L), token);

        assertThat(result.media()).extracting(ChildrenEntitiesDTO::getIndex).containsExactly(8L, 9L);
        assertThat(result.media()).extracting(ChildrenEntitiesDTO::getOrder).containsExactly(1L, 2L);
        verifyNoInteractions(renderer);
        verify(mediaService, never()).store(any(), any(), any(), any(), any());
        assertThat(counter("taurus.image.edit", "outcome", "replayed")).isEqualTo(1);
    }

    @Test
    void refusesTheSameKeyForADifferentRecipe() throws Exception {
        TrackPageImageService service = service(new ImageTransformationService());
        Fixture fixture = fixture();
        when(receipts.findByRequestedByAndRequestKey("user-1", KEY)).thenReturn(Optional.of(receipt(fixture.track.getEntityVersion(), service, 2L)));

        assertThatThrownBy(() -> service.edit(4L, 5L, 6L, KEY, oneCrop(2L), token))
            .isInstanceOf(RequestAlertException.class)
            .extracting(exception -> ((RequestAlertException) exception).getErrorKey())
            .isEqualTo("image.idempotency.conflict");
        assertThat(counter("taurus.image.conflicts", "kind", "fingerprint")).isEqualTo(1);
    }

    /** Una ricevuta che non descrive più la pagina corrente non va rigiocata in silenzio. */
    @Test
    void refusesAReceiptThatIsNoLongerCurrent() throws Exception {
        TrackPageImageService service = service(new ImageTransformationService());
        Fixture fixture = fixture();
        when(receipts.findByRequestedByAndRequestKey("user-1", KEY)).thenReturn(Optional.of(receipt(99L, service, 2L)));

        assertThatThrownBy(() -> service.edit(4L, 5L, 6L, KEY, twoCrops(2L), token))
            .isInstanceOf(RequestAlertException.class)
            .extracting(exception -> ((RequestAlertException) exception).getErrorKey())
            .isEqualTo("image.idempotency.stale");
        assertThat(counter("taurus.image.conflicts", "kind", "stale")).isEqualTo(1);
    }

    @Test
    void refusesAnEditBuiltOnAnOlderTrackVersion() throws Exception {
        TrackPageImageService service = service(new ImageTransformationService());
        fixture();

        assertThatThrownBy(() -> service.edit(4L, 5L, 6L, KEY, twoCrops(1L), token))
            .isInstanceOf(RequestAlertException.class)
            .extracting(exception -> ((RequestAlertException) exception).getErrorKey())
            .isEqualTo("version.conflict");
        assertThat(counter("taurus.image.conflicts", "kind", "version")).isEqualTo(1);
        verify(mediaService, never()).store(any(), any(), any(), any(), any());
    }

    @Test
    void refusesAnEditRequestedByAPlainUser() {
        TrackPageImageService service = service(new ImageTransformationService());

        assertThatThrownBy(() -> service.edit(4L, 5L, 6L, KEY, twoCrops(2L), authentication("ROLE_USER")))
            .isInstanceOf(RequestAlertException.class)
            .extracting(exception -> ((RequestAlertException) exception).getErrorKey())
            .isEqualTo("image.forbidden");
        verifyNoInteractions(tracks, mediaService);
    }

    @Test
    void refusesAnAnalysisRequestedByAPlainUser() {
        TrackPageImageService service = service(new ImageTransformationService());

        assertThatThrownBy(() -> service.analyze(4L, 5L, 6L, authentication("ROLE_USER")))
            .isInstanceOf(RequestAlertException.class)
            .extracting(exception -> ((RequestAlertException) exception).getErrorKey())
            .isEqualTo("image.forbidden");
    }

    /** Oltre il limite di tempo la richiesta risponde 503 invece di tenere occupato il thread. */
    @Test
    void answersUnavailableWhenRenderingExceedsTheTimeLimit() throws Exception {
        properties.getMedia().setImageEditTimeoutSeconds(1);
        ImageTransformationService renderer = mock(ImageTransformationService.class);
        when(renderer.transform(any(), any())).thenAnswer(invocation -> {
            Thread.sleep(5_000);
            return List.of(new byte[]{1});
        });
        TrackPageImageService service = service(renderer);
        fixture();

        assertThatThrownBy(() -> service.edit(4L, 5L, 6L, KEY, twoCrops(2L), token))
            .isInstanceOf(RequestAlertException.class)
            .satisfies(exception -> {
                assertThat(((RequestAlertException) exception).getErrorKey()).isEqualTo("image.timeout");
                assertThat(((RequestAlertException) exception).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            });
        verify(mediaService, never()).store(any(), any(), any(), any(), any());
        assertThat(counter("taurus.image.edit", "outcome", "image.timeout")).isEqualTo(1);
        service.stopRenderer();
    }

    /**
     * Quando la transazione fallisce dopo la scrittura degli asset, la compensazione di
     * {@code MediaService} li rimuove: qui si misura quanti file ha lasciato indietro il tentativo.
     */
    @Test
    void countsTheAssetsLeftBehindByAFailedTransaction() throws Exception {
        TrackPageImageService service = service(new ImageTransformationService());
        fixture();
        when(tracks.saveAndFlush(any())).thenThrow(new IllegalStateException("database is gone"));

        assertThatThrownBy(() -> service.edit(4L, 5L, 6L, KEY, twoCrops(2L), token)).isInstanceOf(IllegalStateException.class);

        assertThat(counter("taurus.image.storage.compensations", null, null)).isEqualTo(2);
        assertThat(counter("taurus.image.edit", "outcome", "error")).isEqualTo(1);
    }

    @Test
    void recordsTheAnalysisOfAPage() throws Exception {
        TrackPageImageService service = service(new ImageTransformationService());
        fixture();

        TrackPageImageDTOs.Analysis analysis = service.analyze(4L, 5L, 6L, token);

        assertThat(analysis.width()).isEqualTo(20);
        assertThat(analysis.height()).isEqualTo(30);
        assertThat(counter("taurus.image.analysis", "outcome", "done")).isEqualTo(1);
    }

    @Test
    void recordsTheOutcomeOfAnAnalysisThatCannotFindThePage() {
        TrackPageImageService service = service(new ImageTransformationService());
        when(tracks.findByIdAndDeletedFalse(4L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.analyze(4L, 5L, 6L, token)).isInstanceOf(RequestAlertException.class);

        assertThat(counter("taurus.image.analysis", "outcome", "image.notfound")).isEqualTo(1);
    }

    private TrackPageImageService service(ImageTransformationService renderer) {
        PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        return new TrackPageImageService(
            tracks,
            media,
            receipts,
            mediaService,
            renderer,
            new ObjectMapper(),
            transactions,
            new TrackPageImageMetrics(registry),
            new TenantSchemaNameResolver(),
            properties
        );
    }

    /** Una traccia con due pagine, di cui la prima è quella da modificare, e due asset in arrivo. */
    private Fixture fixture() throws Exception {
        Tracks track = new Tracks();
        track.setId(4L);
        track.setEntityVersion(2L);
        SheetsMusic score = new SheetsMusic();
        score.setId(5L);
        Media oldMedia = new Media();
        oldMedia.setId(6L);
        score.getMedia().add(oldMedia);
        Media following = new Media();
        following.setId(7L);
        score.getMedia().add(following);
        track.getScores().add(score);
        Media firstNew = new Media();
        firstNew.setId(8L);
        Media secondNew = new Media();
        secondNew.setId(9L);
        MediaDTO firstStored = new MediaDTO();
        firstStored.setId(8L);
        firstStored.setName("page-edited-1.png");
        MediaDTO secondStored = new MediaDTO();
        secondStored.setId(9L);
        secondStored.setName("page-edited-2.png");
        byte[] source = png();

        when(tracks.findByIdAndDeletedFalse(4L)).thenReturn(Optional.of(track));
        when(mediaService.getContent(6L, token)).thenReturn(new MediaService.MediaContent("page.png", "image/png", source.length, source));
        when(mediaService.store(any(), any(), any(), any(), any())).thenReturn(firstStored, secondStored);
        when(media.getReferenceById(8L)).thenReturn(firstNew);
        when(media.getReferenceById(9L)).thenReturn(secondNew);
        when(tracks.saveAndFlush(track)).thenAnswer(invocation -> {
            track.setEntityVersion(3L);
            return track;
        });
        return new Fixture(track, score, following, firstNew, secondNew);
    }

    /** La ricevuta di un primo tentativo: l'impronta è quella che calcolerebbe il servizio stesso. */
    private TrackPageEditReceipt receipt(Long resultVersion, TrackPageImageService service, long expectedTrackVersion) throws Exception {
        TrackPageEditReceipt receipt = new TrackPageEditReceipt();
        receipt.setRequestKey(KEY);
        receipt.setRequestedBy("user-1");
        receipt.setRequestFingerprint(fingerprintOf(service, twoCrops(expectedTrackVersion)));
        receipt.setTrackId(4L);
        receipt.setSheetMusicId(5L);
        receipt.setSourceMediaId(6L);
        receipt.setResultTrackVersion(resultVersion);
        receipt.setResultMediaJson("[{\"index\":8,\"name\":\"page-edited-1.png\",\"order\":1},{\"index\":9,\"name\":\"page-edited-2.png\",\"order\":2}]");
        return receipt;
    }

    private static String fingerprintOf(TrackPageImageService service, TrackPageImageDTOs.EditRequest request) throws Exception {
        var method = TrackPageImageService.class.getDeclaredMethod("fingerprint", Long.class, Long.class, Long.class, TrackPageImageDTOs.EditRequest.class);
        method.setAccessible(true);
        return (String) method.invoke(service, 4L, 5L, 6L, request);
    }

    private static TrackPageImageDTOs.EditRequest twoCrops(long expectedTrackVersion) {
        return new TrackPageImageDTOs.EditRequest(
            expectedTrackVersion,
            1,
            0,
            0,
            List.of(new TrackPageImageDTOs.Crop(0, 0, 0.5, 1), new TrackPageImageDTOs.Crop(0.5, 0, 0.5, 1)),
            false,
            0,
            0,
            false,
            null
        );
    }

    private static TrackPageImageDTOs.EditRequest oneCrop(long expectedTrackVersion) {
        return new TrackPageImageDTOs.EditRequest(
            expectedTrackVersion,
            1,
            0,
            0,
            List.of(new TrackPageImageDTOs.Crop(0, 0, 0.5, 1)),
            false,
            0,
            0,
            false,
            null
        );
    }

    private double counter(String name, String tag, String value) {
        var search = registry.find(name);
        if (tag != null) search = search.tag(tag, value);
        return search.counters().stream().mapToDouble(io.micrometer.core.instrument.Counter::count).sum();
    }

    private static byte[] png() throws Exception {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(new BufferedImage(20, 30, BufferedImage.TYPE_INT_RGB), "png", output);
            return output.toByteArray();
        }
    }

    private static JwtAuthenticationToken authentication(String authority) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject("user-1")
            .claim("tenant", "BMCDG")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(authority)));
    }

    private record Fixture(Tracks track, SheetsMusic score, Media following, Media firstNew, Media secondNew) {
    }
}
