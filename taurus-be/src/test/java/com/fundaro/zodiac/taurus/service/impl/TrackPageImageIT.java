package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.IntegrationTest;
import com.fundaro.zodiac.taurus.domain.CommonFieldsOpenSearch;
import com.fundaro.zodiac.taurus.domain.Media;
import com.fundaro.zodiac.taurus.domain.SheetsMusic;
import com.fundaro.zodiac.taurus.domain.Tenants;
import com.fundaro.zodiac.taurus.domain.Tracks;
import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaNameResolver;
import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaProvisioningService;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import com.fundaro.zodiac.taurus.repository.TenantsRepository;
import com.fundaro.zodiac.taurus.service.MediaService;
import com.fundaro.zodiac.taurus.service.dto.MediaDTO;
import com.fundaro.zodiac.taurus.service.dto.TrackPageImageDTOs;
import com.fundaro.zodiac.taurus.test.DockerRequirement;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.TestPropertySource;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * La modifica di una pagina su PostgreSQL e sullo storage veri: catena autorizzata, isolamento fra
 * tenant, replay di una ricevuta, due salvataggi concorrenti sulla stessa pagina, input malevolo e
 * limite di tempo del rendering.
 *
 * <p>Il rendering lento è l'unica cosa simulata: un'immagine abbastanza grande da superare il
 * limite renderebbe la prova lenta e dipendente dalla macchina, mentre quello che conta è il
 * comportamento intorno al limite, cioè il {@code 503} e l'assenza di asset e ricevute a metà.
 */
@IntegrationTest
@TestPropertySource(
    properties = {
        "application.base-path=${java.io.tmpdir}/taurus-image-it",
        // Cinque secondi: due ordini di grandezza oltre il rendering di una pagina di prova, così
        // il limite scatta solo per il rendering simulato e non per una macchina occupata.
        "application.media.image-edit-timeout-seconds=5",
        "spring.liquibase.contexts=test",
        "spring.datasource.hikari.maximum-pool-size=8",
        "spring.datasource.hikari.minimum-idle=2",
        "spring.security.oauth2.client.registration.oidc.client-id=test",
        "spring.security.oauth2.client.registration.oidc.client-secret=test",
    }
)
class TrackPageImageIT {

    @MockBean
    ClientRegistrationRepository clientRegistrationRepository;
    @MockBean
    JwtDecoder jwtDecoder;

    @SpyBean
    ImageTransformationService imageTransformationService;

    @Autowired
    TenantSchemaProvisioningService provisioningService;
    @Autowired
    TenantSchemaNameResolver schemaNameResolver;
    @Autowired
    TenantTransactionExecutor transactionExecutor;
    @Autowired
    TenantsRepository tenantsRegistry;
    @Autowired
    TrackPageImageService service;
    @Autowired
    MediaService mediaService;
    @Autowired
    EntityManager entityManager;
    @Autowired
    JdbcTemplate jdbcTemplate;

    private final String ownerTenant = "image-owner-" + UUID.randomUUID();
    private final String intruderTenant = "image-intruder-" + UUID.randomUUID();

    @BeforeAll
    static void requireDocker() {
        DockerRequirement.required();
    }

    @BeforeEach
    void provisionBothTenants() {
        registerTenant(ownerTenant);
        registerTenant(intruderTenant);
        provisioningService.provision(ownerTenant);
        provisioningService.provision(intruderTenant);
    }

    @AfterEach
    void dropBothTenants() {
        try {
            provisioningService.dropSchema(ownerTenant);
            provisioningService.dropSchema(intruderTenant);
        } finally {
            unregisterTenant(ownerTenant);
            unregisterTenant(intruderTenant);
        }
    }

    @Test
    void replacesThePageWithTheCropsAndBumpsTheTrackVersion() {
        Page page = createPage(ownerTenant);

        TrackPageImageDTOs.EditResult result = transactionExecutor.execute(ownerTenant, () ->
            service.edit(page.trackId(), page.scoreId(), page.mediaId(), UUID.randomUUID(), twoCrops(page.version()), archivist(ownerTenant))
        );

        assertThat(result.media()).hasSize(2);
        assertThat(result.trackVersion()).isGreaterThan(page.version());
        assertThat(activeMedia(ownerTenant, page.scoreId()))
            .containsExactly(result.media().get(0).getIndex(), result.media().get(1).getIndex());
        transactionExecutor.execute(ownerTenant, () ->
            assertThat(mediaService.getContent(result.media().get(0).getIndex(), archivist(ownerTenant)).mimeType()).isEqualTo("image/png")
        );
    }

    /** La pagina sostituita non esiste più nella parte: un secondo tentativo non la ritrova. */
    @Test
    void doesNotFindThePageTwice() {
        Page page = createPage(ownerTenant);
        transactionExecutor.execute(ownerTenant, () ->
            service.edit(page.trackId(), page.scoreId(), page.mediaId(), UUID.randomUUID(), twoCrops(page.version()), archivist(ownerTenant))
        );

        assertThatThrownBy(() -> transactionExecutor.execute(ownerTenant, () ->
            service.edit(page.trackId(), page.scoreId(), page.mediaId(), UUID.randomUUID(), twoCrops(page.version() + 1), archivist(ownerTenant))
        ))
            .isInstanceOf(RequestAlertException.class)
            .extracting(exception -> ((RequestAlertException) exception).getErrorKey())
            .isEqualTo("image.notFound");
    }

    @Test
    void doesNotSeeAPageOfAnotherTenant() {
        Page page = createPage(ownerTenant);

        assertThatThrownBy(() -> transactionExecutor.execute(intruderTenant, () ->
            service.analyze(page.trackId(), page.scoreId(), page.mediaId(), archivist(intruderTenant))
        ))
            .isInstanceOf(RequestAlertException.class)
            .extracting(exception -> ((RequestAlertException) exception).getErrorKey())
            .isEqualTo("image.notFound");

        assertThatThrownBy(() -> transactionExecutor.execute(intruderTenant, () ->
            service.edit(page.trackId(), page.scoreId(), page.mediaId(), UUID.randomUUID(), twoCrops(page.version()), archivist(intruderTenant))
        ))
            .isInstanceOf(RequestAlertException.class)
            .extracting(exception -> ((RequestAlertException) exception).getErrorKey())
            .isEqualTo("image.notFound");
        assertThat(activeMedia(ownerTenant, page.scoreId())).containsExactly(page.mediaId());
    }

    /** Lo stesso tentativo ripetuto: la ricevuta restituisce la prima risposta, senza nuovi asset. */
    @Test
    void replaysTheSameAttemptWithoutASecondAsset() {
        Page page = createPage(ownerTenant);
        UUID key = UUID.randomUUID();

        TrackPageImageDTOs.EditResult first = transactionExecutor.execute(ownerTenant, () ->
            service.edit(page.trackId(), page.scoreId(), page.mediaId(), key, twoCrops(page.version()), archivist(ownerTenant))
        );
        TrackPageImageDTOs.EditResult replay = transactionExecutor.execute(ownerTenant, () ->
            service.edit(page.trackId(), page.scoreId(), page.mediaId(), key, twoCrops(page.version()), archivist(ownerTenant))
        );

        assertThat(replay.media()).extracting(child -> child.getIndex()).isEqualTo(first.media().stream().map(child -> child.getIndex()).toList());
        assertThat(replay.trackVersion()).isEqualTo(first.trackVersion());
        assertThat(activeMedia(ownerTenant, page.scoreId())).hasSize(2);
        assertThat(receiptCount(ownerTenant)).isEqualTo(1);
    }

    @Test
    void refusesTheSameKeyUsedForADifferentRecipe() {
        Page page = createPage(ownerTenant);
        UUID key = UUID.randomUUID();
        transactionExecutor.execute(ownerTenant, () ->
            service.edit(page.trackId(), page.scoreId(), page.mediaId(), key, twoCrops(page.version()), archivist(ownerTenant))
        );

        assertThatThrownBy(() -> transactionExecutor.execute(ownerTenant, () ->
            service.edit(page.trackId(), page.scoreId(), page.mediaId(), key, grayscale(page.version()), archivist(ownerTenant))
        ))
            .isInstanceOf(RequestAlertException.class)
            .extracting(exception -> ((RequestAlertException) exception).getErrorKey())
            .isEqualTo("image.idempotency.conflict");
    }

    /** Due salvataggi concorrenti sulla stessa pagina: uno vince, gli altri non modificano nulla. */
    @Test
    void letsOnlyOneConcurrentSaveWin() throws Exception {
        Page page = createPage(ownerTenant);

        List<Object> outcomes = inParallel(4, () -> {
            try {
                return transactionExecutor.execute(ownerTenant, () ->
                    service.edit(page.trackId(), page.scoreId(), page.mediaId(), UUID.randomUUID(), twoCrops(page.version()), archivist(ownerTenant))
                );
            } catch (RuntimeException exception) {
                return exception;
            }
        });

        assertThat(outcomes).filteredOn(TrackPageImageDTOs.EditResult.class::isInstance).hasSize(1);
        assertThat(outcomes).filteredOn(RuntimeException.class::isInstance).isNotEmpty();
        assertThat(activeMedia(ownerTenant, page.scoreId())).hasSize(2);
    }

    /**
     * Byte che si dichiarano PNG ma non lo sono: nessun lettore ImageIO li riconosce e la risposta
     * è un 415, non un errore interno. Il formato lo decidono i byte, non il nome del file.
     */
    @Test
    void refusesBytesThatOnlyClaimToBeAnImage() {
        Page page = createPage(ownerTenant, "non sono un'immagine: \u0000\u0001\u0002".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> transactionExecutor.execute(ownerTenant, () ->
            service.analyze(page.trackId(), page.scoreId(), page.mediaId(), archivist(ownerTenant))
        ))
            .isInstanceOf(RequestAlertException.class)
            .extracting(exception -> ((RequestAlertException) exception).getErrorKey())
            .isEqualTo("image.unsupported");
    }

    /**
     * Una decompression bomb: l'header dichiara dimensioni enormi su pochi byte compressi. Il
     * rifiuto deve arrivare dall'header, prima di allocare i pixel.
     */
    @Test
    void refusesAnImageThatDeclaresTooManyPixels() throws Exception {
        Page page = createPage(ownerTenant, bomb());

        assertThatThrownBy(() -> transactionExecutor.execute(ownerTenant, () ->
            service.analyze(page.trackId(), page.scoreId(), page.mediaId(), archivist(ownerTenant))
        ))
            .isInstanceOf(RequestAlertException.class)
            .extracting(exception -> ((RequestAlertException) exception).getErrorKey())
            .isEqualTo("image.dimensions.tooLarge");
    }

    @Test
    void answersUnavailableAndWritesNothingWhenRenderingExceedsTheTimeLimit() {
        Page page = createPage(ownerTenant);
        doAnswer(invocation -> {
            Thread.sleep(30_000);
            return List.of(new byte[]{1});
        }).when(imageTransformationService).transform(any(), any());

        assertThatThrownBy(() -> transactionExecutor.execute(ownerTenant, () ->
            service.edit(page.trackId(), page.scoreId(), page.mediaId(), UUID.randomUUID(), twoCrops(page.version()), archivist(ownerTenant))
        ))
            .isInstanceOf(RequestAlertException.class)
            .satisfies(exception -> {
                assertThat(((RequestAlertException) exception).getErrorKey()).isEqualTo("image.timeout");
                assertThat(((RequestAlertException) exception).getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            });

        assertThat(activeMedia(ownerTenant, page.scoreId())).containsExactly(page.mediaId());
        assertThat(receiptCount(ownerTenant)).isZero();
    }

    /** Una pagina vera: l'asset è scritto dallo storage del tenant, come in produzione. */
    private Page createPage(String tenant) {
        return createPage(tenant, page());
    }

    private Page createPage(String tenant, byte[] content) {
        return transactionExecutor.execute(tenant, () -> {
            MediaDTO stored = mediaService.store(content, "page.png", "image/png", "scores", archivist(tenant));
            Tracks track = audited(new Tracks(), "Brano con pagina");
            track.getType().add("ORIGINAL");
            SheetsMusic score = new SheetsMusic();
            score.getMedia().add(entityManager.getReference(Media.class, stored.getId()));
            track.getScores().add(score);
            entityManager.persist(track);
            entityManager.flush();
            return new Page(track.getId(), track.getScores().get(0).getId(), stored.getId(), track.getEntityVersion());
        });
    }

    private List<Long> activeMedia(String tenant, Long scoreId) {
        return jdbcTemplate.queryForList(
            "SELECT media_asset_id FROM " + quote(schemaNameResolver.resolve(tenant))
                + ".sheet_music_media WHERE sheet_music_id = ? AND deleted = FALSE ORDER BY display_order",
            Long.class,
            scoreId
        );
    }

    private long receiptCount(String tenant) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + quote(schemaNameResolver.resolve(tenant)) + ".track_page_edit_receipt",
            Long.class
        );
    }

    private List<Object> inParallel(int workers, Callable<Object> action) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(workers);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (int worker = 0; worker < workers; worker++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return action.call();
                }));
            }
            start.countDown();

            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> future : futures) outcomes.add(future.get(60, TimeUnit.SECONDS));
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
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

    private void registerTenant(String code) {
        Date now = new Date();
        Tenants tenant = new Tenants();
        tenant.setName(code);
        tenant.setCode(code);
        tenant.setActive(true);
        tenant.setDeleted(false);
        tenant.setInsertBy("user-1");
        tenant.setInsertDate(now);
        tenant.setEditBy("user-1");
        tenant.setEditDate(now);
        tenant.setEntityVersion(0L);
        tenantsRegistry.save(tenant);
    }

    private void unregisterTenant(String code) {
        tenantsRegistry.findByCodeAndDeletedFalse(code).ifPresent(tenantsRegistry::delete);
    }

    private static TrackPageImageDTOs.EditRequest twoCrops(Long expectedTrackVersion) {
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

    private static TrackPageImageDTOs.EditRequest grayscale(Long expectedTrackVersion) {
        return new TrackPageImageDTOs.EditRequest(expectedTrackVersion, 1, 0, 0, List.of(), true, 0, 0, false, null);
    }

    private static byte[] page() {
        BufferedImage image = new BufferedImage(600, 800, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, 600, 800);
        graphics.setColor(Color.BLACK);
        for (int line = 0; line < 10; line++) graphics.drawLine(60, 100 + line * 60, 540, 100 + line * 60);
        graphics.dispose();
        return encode(image);
    }

    /**
     * Un PNG minuscolo che dichiara un'immagine da miliardi di pixel: i byte sono pochi, l'header
     * no. Si costruisce a mano perché nessun encoder produce un header del genere.
     */
    private static byte[] bomb() throws Exception {
        byte[] real = encode(new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
        byte[] bomb = real.clone();
        // IHDR: 8 byte di firma, 4 di lunghezza, 4 di tipo, poi larghezza e altezza big endian.
        writeInt(bomb, 16, 60_000);
        writeInt(bomb, 20, 60_000);
        return bomb;
    }

    private static void writeInt(byte[] target, int offset, int value) {
        target[offset] = (byte) (value >>> 24);
        target[offset + 1] = (byte) (value >>> 16);
        target[offset + 2] = (byte) (value >>> 8);
        target[offset + 3] = (byte) value;
    }

    private static byte[] encode(BufferedImage image) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", output);
            return output.toByteArray();
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to encode the test page", exception);
        }
    }

    private static String quote(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private static JwtAuthenticationToken archivist(String tenant) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject("user-1")
            .claim("tenant", tenant)
            .claim("given_name", "Mario")
            .claim("family_name", "Rossi")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority("ROLE_ARCHIVIST")));
    }

    private record Page(Long trackId, Long scoreId, Long mediaId, Long version) {
    }
}
