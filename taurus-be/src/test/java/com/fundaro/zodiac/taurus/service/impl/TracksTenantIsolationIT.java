package com.fundaro.zodiac.taurus.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fundaro.zodiac.taurus.IntegrationTest;
import com.fundaro.zodiac.taurus.domain.SheetsMusic;
import com.fundaro.zodiac.taurus.domain.Tracks;
import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaNameResolver;
import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaProvisioningService;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import com.fundaro.zodiac.taurus.repository.TracksRepository;
import com.fundaro.zodiac.taurus.service.TracksService;
import com.fundaro.zodiac.taurus.service.dto.TracksDTO;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.TestPropertySource;

/**
 * Verifica che tracce e spartiti non attraversino il confine fra due schemi tenant.
 *
 * <p>I due schemi vengono creati per ogni test, quindi le identità generate partono dallo stesso
 * valore in entrambi: una traccia del primo tenant e una del secondo condividono lo stesso id.
 * È la condizione in cui un errore di instradamento dello schema diventa osservabile, in
 * particolare per le query native di rinumerazione degli spartiti, che non qualificano lo schema
 * e dipendono esclusivamente dalla connessione scelta per il tenant corrente.
 */
@IntegrationTest
@TestPropertySource(
    properties = {
        "spring.liquibase.contexts=test",
        "spring.datasource.hikari.maximum-pool-size=4",
        "spring.datasource.hikari.minimum-idle=1",
        "spring.security.oauth2.client.registration.oidc.client-id=test",
        "spring.security.oauth2.client.registration.oidc.client-secret=test",
    }
)
class TracksTenantIsolationIT {

    @MockBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockBean
    private JwtDecoder jwtDecoder;

    private final String firstTenant = "tracks-isolation-a-" + UUID.randomUUID();
    private final String secondTenant = "tracks-isolation-b-" + UUID.randomUUID();

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
    private TracksRepository tracksRepository;

    @Autowired
    private TracksService tracksService;

    @BeforeEach
    void provisionBothTenants() {
        provisioningService.provision(firstTenant);
        provisioningService.provision(secondTenant);
    }

    @AfterEach
    void dropBothTenants() {
        try {
            provisioningService.dropSchema(firstTenant);
        } finally {
            provisioningService.dropSchema(secondTenant);
        }
    }

    @Test
    void keepsTracksOfOneTenantInvisibleToTheOther() {
        Long trackId = transactionExecutor.execute(
            firstTenant,
            () -> {
                return persistTrack("Marcia sinfonica", 2);
            }
        );

        transactionExecutor.execute(
            secondTenant,
            () -> {
                assertThat(tracksRepository.findByIdAndDeletedFalse(trackId)).isEmpty();
                assertThat(tracksRepository.findByIdForUpdate(trackId)).isEmpty();
                assertThat(tracksRepository.findAll()).isEmpty();
                assertThat(tracksRepository.count()).isZero();
            }
        );

        transactionExecutor.execute(
            firstTenant,
            () -> {
                Tracks reloaded = tracksRepository.findByIdAndDeletedFalse(trackId).orElseThrow();
                assertThat(reloaded.getName()).isEqualTo("Marcia sinfonica");
                assertThat(reloaded.getScores()).hasSize(2);
            }
        );
    }

    @Test
    void refusesToUpdateATrackThroughAnotherTenantSchema() {
        Long trackId = transactionExecutor.execute(
            firstTenant,
            () -> {
                return persistTrack("Ouverture", 1);
            }
        );

        TracksDTO request = new TracksDTO();
        request.setName("Rinominata dal secondo tenant");

        assertThatThrownBy(() ->
            transactionExecutor.execute(
                secondTenant,
                () -> {
                    tracksService.update(trackId, request, authentication(secondTenant));
                }
            )
        )
            .isInstanceOf(RequestAlertException.class)
            .hasMessageContaining("Entity not found");

        transactionExecutor.execute(
            firstTenant,
            () -> {
                assertThat(tracksRepository.findByIdAndDeletedFalse(trackId).orElseThrow().getName()).isEqualTo("Ouverture");
            }
        );
    }

    @Test
    void keepsTheNativeScoreReorderingInsideTheCurrentTenantSchema() {
        Long firstTrackId = transactionExecutor.execute(
            firstTenant,
            () -> {
                return persistTrack("Concerto", 3);
            }
        );
        Long secondTrackId = transactionExecutor.execute(
            secondTenant,
            () -> {
                return persistTrack("Concerto", 3);
            }
        );
        assertThat(secondTrackId).isEqualTo(firstTrackId);

        List<Integer> untouchedOrders = scoreOrders(firstTenant, firstTrackId);
        assertThat(untouchedOrders).hasSize(3).allMatch(order -> order >= 0);

        transactionExecutor.execute(
            secondTenant,
            () -> {
                assertThat(tracksRepository.moveActiveScoreOrdersToTemporaryRange(secondTrackId)).isEqualTo(3);
            }
        );

        assertThat(scoreOrders(firstTenant, firstTrackId)).isEqualTo(untouchedOrders);
        assertThat(scoreOrders(secondTenant, secondTrackId)).hasSize(3).allMatch(order -> order < 0);
    }

    @Test
    void keepsTheNativeScoreRestoreInsideTheCurrentTenantSchema() {
        Long firstTrackId = transactionExecutor.execute(
            firstTenant,
            () -> {
                return persistTrack("Suite", 2);
            }
        );
        Long secondTrackId = transactionExecutor.execute(
            secondTenant,
            () -> {
                return persistTrack("Suite", 2);
            }
        );
        assertThat(secondTrackId).isEqualTo(firstTrackId);

        List<Long> firstScoreIds = scoreIds(firstTenant, firstTrackId);
        List<Long> secondScoreIds = scoreIds(secondTenant, secondTrackId);
        assertThat(secondScoreIds).isEqualTo(firstScoreIds);

        List<Integer> untouchedOrders = scoreOrders(firstTenant, firstTrackId);

        transactionExecutor.execute(
            secondTenant,
            () -> {
                assertThat(tracksRepository.restoreActiveScoreOrder(secondTrackId, secondScoreIds.get(0), 41)).isEqualTo(1);
            }
        );

        assertThat(scoreOrders(firstTenant, firstTrackId)).isEqualTo(untouchedOrders);
        assertThat(scoreOrders(secondTenant, secondTrackId)).first().isEqualTo(41);
    }

    private Long persistTrack(String name, int scores) {
        Date now = new Date();
        Tracks track = new Tracks();
        track.setName(name);
        track.setDeleted(false);
        track.setInsertBy("test");
        track.setInsertDate(now);
        track.setEditBy("test");
        track.setEditDate(now);
        for (int index = 0; index < scores; index++) {
            SheetsMusic score = new SheetsMusic();
            score.setDescription(name + " - parte " + (index + 1));
            track.getScores().add(score);
        }
        entityManager.persist(track);
        entityManager.flush();
        return track.getId();
    }

    private List<Integer> scoreOrders(String tenantCode, Long trackId) {
        return jdbcTemplate.queryForList(
            "SELECT display_order FROM " + quote(schemaNameResolver.resolve(tenantCode)) + ".sheet_music WHERE track_id = ? AND deleted = FALSE ORDER BY id",
            Integer.class,
            trackId
        );
    }

    private List<Long> scoreIds(String tenantCode, Long trackId) {
        return jdbcTemplate.queryForList(
            "SELECT id FROM " + quote(schemaNameResolver.resolve(tenantCode)) + ".sheet_music WHERE track_id = ? AND deleted = FALSE ORDER BY id",
            Long.class,
            trackId
        );
    }

    private JwtAuthenticationToken authentication(String tenantCode) {
        Instant now = Instant.now();
        Jwt jwt = Jwt
            .withTokenValue("token")
            .header("alg", "none")
            .subject("user-1")
            .claim("tenant", tenantCode)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .build();
        return new JwtAuthenticationToken(jwt);
    }

    private String quote(String identifier) {
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }
}
