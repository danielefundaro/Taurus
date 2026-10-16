package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.IntegrationTest;
import com.fundaro.zodiac.taurus.domain.CommonFieldsOpenSearch;
import com.fundaro.zodiac.taurus.domain.Instruments;
import com.fundaro.zodiac.taurus.domain.Media;
import com.fundaro.zodiac.taurus.domain.SheetsMusic;
import com.fundaro.zodiac.taurus.domain.Tracks;
import com.fundaro.zodiac.taurus.domain.criteria.MediaCriteria;
import com.fundaro.zodiac.taurus.domain.enumeration.MediaAssetStatus;
import com.fundaro.zodiac.taurus.domain.enumeration.StateEnum;
import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaNameResolver;
import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaProvisioningService;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import com.fundaro.zodiac.taurus.security.AuthoritiesConstants;
import com.fundaro.zodiac.taurus.service.MediaService;
import com.fundaro.zodiac.taurus.service.dto.MediaDTO;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
@TestPropertySource(
    properties = {
        "application.base-path=D:/data",
        "spring.liquibase.contexts=test",
        "spring.datasource.hikari.maximum-pool-size=4",
        "spring.datasource.hikari.minimum-idle=1",
        "spring.security.oauth2.client.registration.oidc.client-id=test",
        "spring.security.oauth2.client.registration.oidc.client-secret=test",
    }
)
class MediaServiceImplIT {

    @MockBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockBean
    private JwtDecoder jwtDecoder;

    private final String tenantCode = "media-visibility-it-" + UUID.randomUUID();
    private final String memberKey = "media-member-" + UUID.randomUUID();
    private final String externalKey = "media-external-" + UUID.randomUUID();
    private final List<Long> identityIds = new ArrayList<>();

    @Autowired
    private TenantSchemaProvisioningService provisioningService;

    @Autowired
    private TenantSchemaNameResolver schemaNameResolver;

    @Autowired
    private TenantTransactionExecutor transactionExecutor;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MediaService mediaService;

    private Pages pages;

    @BeforeEach
    void provisionTenant() {
        provisioningService.provision(tenantCode);
        pages = createCatalog();
    }

    @AfterEach
    void dropTenant() {
        try {
            provisioningService.dropSchema(tenantCode);
        } finally {
            transactionExecutor.execute(
                TenantSchemaNameResolver.DEFAULT_SCHEMA,
                () -> identityIds.forEach(id -> jdbcTemplate.update("DELETE FROM public.user_identity WHERE id = ?", id))
            );
        }
    }

    @Test
    void standardUsersSeePagesOfCompleteAndPublicTracksForTheirInstruments() {
        JwtAuthenticationToken member = authentication(memberKey, AuthoritiesConstants.USER);

        transactionExecutor.execute(tenantCode, () -> {
            assertThat(mediaService.findOne(pages.completeOwn(), member)).isPresent();
            assertThat(mediaService.findOne(pages.publicOwn(), member)).isPresent();
            assertThat(mediaService.findOne(pages.draftOwn(), member)).isEmpty();
            assertThat(mediaService.findOne(pages.completeOther(), member)).isEmpty();
            assertThat(mediaService.findEntitiesByCriteria(new MediaCriteria(), PageRequest.of(0, 20), member).map(MediaDTO::getId))
                .containsExactlyInAnyOrder(pages.completeOwn(), pages.publicOwn());
            assertThat(mediaService.count(new MediaCriteria(), member)).isEqualTo(2);
            return null;
        });
        assertThatThrownBy(() -> transactionExecutor.execute(tenantCode, () -> mediaService.streamFile(pages.draftOwn(), member)))
            .isInstanceOfSatisfying(RequestAlertException.class, error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void externalUsersSeePagesOfPublicTracksForTheirInstruments() {
        JwtAuthenticationToken external = authentication(externalKey, AuthoritiesConstants.USER_EXTERNAL);

        transactionExecutor.execute(tenantCode, () -> {
            assertThat(mediaService.findOne(pages.publicOwn(), external)).isPresent();
            assertThat(mediaService.findOne(pages.completeOwn(), external)).isEmpty();
            assertThat(mediaService.count(new MediaCriteria(), external)).isEqualTo(1);
            return null;
        });
    }

    @Test
    void removedInstrumentAssignmentHidesPages() {
        String schema = quote(schemaNameResolver.resolve(tenantCode));
        jdbcTemplate.update(
            "UPDATE " + schema + ".user_instrument SET deleted = TRUE WHERE user_id = (SELECT id FROM " + schema + ".app_user WHERE keycloak_id = ?)",
            memberKey
        );
        JwtAuthenticationToken member = authentication(memberKey, AuthoritiesConstants.USER);

        transactionExecutor.execute(tenantCode, () -> {
            assertThat(mediaService.findOne(pages.completeOwn(), member)).isEmpty();
            assertThat(mediaService.count(new MediaCriteria(), member)).isZero();
            return null;
        });
    }

    @Test
    void privilegedUsersKeepAccessToEveryPage() {
        JwtAuthenticationToken archivist = authentication("media-archivist", AuthoritiesConstants.ARCHIVIST);

        transactionExecutor.execute(tenantCode, () -> {
            assertThat(mediaService.findOne(pages.draftOwn(), archivist)).isPresent();
            assertThat(mediaService.findOne(pages.completeOther(), archivist)).isPresent();
            return null;
        });
    }

    private Pages createCatalog() {
        Pages created = transactionExecutor.execute(tenantCode, () -> {
            Instruments own = audited(new Instruments(), "Flicorno baritono");
            Instruments other = audited(new Instruments(), "Tuba");
            entityManager.persist(own);
            entityManager.persist(other);
            Media completeOwn = persistMedia("complete-own");
            Media publicOwn = persistMedia("public-own");
            Media draftOwn = persistMedia("draft-own");
            Media completeOther = persistMedia("complete-other");
            persistTrack(StateEnum.COMPLETE, own, completeOwn);
            persistTrack(StateEnum.PUBLIC, own, publicOwn);
            persistTrack(StateEnum.DRAFT, own, draftOwn);
            persistTrack(StateEnum.COMPLETE, other, completeOther);
            entityManager.flush();
            return new Pages(own.getId(), completeOwn.getId(), publicOwn.getId(), draftOwn.getId(), completeOther.getId());
        });
        assignInstrument(memberKey, created.ownInstrument());
        assignInstrument(externalKey, created.ownInstrument());
        return created;
    }

    private void assignInstrument(String keycloakId, Long instrumentId) {
        Long identityId = transactionExecutor.execute(
            TenantSchemaNameResolver.DEFAULT_SCHEMA,
            () -> jdbcTemplate.queryForObject(
                "INSERT INTO public.user_identity (keycloak_id, created_at) VALUES (?, CURRENT_TIMESTAMP) RETURNING id",
                Long.class,
                keycloakId
            )
        );
        identityIds.add(identityId);
        String schema = quote(schemaNameResolver.resolve(tenantCode));
        Long userId = jdbcTemplate.queryForObject(
            "INSERT INTO " + schema + ".app_user (user_identity_id, keycloak_id, name) VALUES (?, ?, ?) RETURNING id",
            Long.class,
            identityId,
            keycloakId,
            "Socio"
        );
        jdbcTemplate.update("INSERT INTO " + schema + ".user_instrument (user_id, instrument_id) VALUES (?, ?)", userId, instrumentId);
    }

    private void persistTrack(StateEnum state, Instruments instrument, Media page) {
        Tracks track = audited(new Tracks(), "Track " + state);
        track.setState(state);
        SheetsMusic score = new SheetsMusic();
        score.getMedia().add(page);
        score.getInstruments().add(instrument);
        track.getScores().add(score);
        entityManager.persist(track);
    }

    private Media persistMedia(String name) {
        String sha256 = UUID.randomUUID().toString().replace("-", "").repeat(2);
        Media media = audited(new Media(), name);
        media.setStorageKey("scores/" + UUID.randomUUID() + "/" + sha256 + ".png");
        media.setOriginalFilename(name + ".png");
        media.setMimeType("image/png");
        media.setFileExtension("png");
        media.setFileSize(1);
        media.setSha256(sha256);
        media.setStatus(MediaAssetStatus.READY);
        entityManager.persist(media);
        return media;
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

    private JwtAuthenticationToken authentication(String keycloakId, String authority) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(keycloakId).claim("tenant", tenantCode)
            .issuedAt(now).expiresAt(now.plusSeconds(300)).build();
        return new JwtAuthenticationToken(jwt, List.of(new SimpleGrantedAuthority(authority)));
    }

    private static String quote(String identifier) {
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }

    private record Pages(Long ownInstrument, Long completeOwn, Long publicOwn, Long draftOwn, Long completeOther) {}
}
