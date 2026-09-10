package com.fundaro.zodiac.taurus.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.fundaro.zodiac.taurus.IntegrationTest;
import com.fundaro.zodiac.taurus.domain.enumeration.MediaAssetStatus;
import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaNameResolver;
import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaProvisioningService;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import com.fundaro.zodiac.taurus.repository.TracksRepository;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
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
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;

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
class RelationalSoftDeleteIT {

    @MockBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockBean
    private JwtDecoder jwtDecoder;

    private final String tenantCode = "soft-delete-it-" + UUID.randomUUID();
    private final String identityKey = "soft-delete-identity-" + UUID.randomUUID();

    private Long identityId;

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

    @BeforeEach
    void provisionTenant() {
        identityId = transactionExecutor.execute(
            TenantSchemaNameResolver.DEFAULT_SCHEMA,
            () ->
                jdbcTemplate.queryForObject(
                    "INSERT INTO public.user_identity (keycloak_id, created_at) VALUES (?, CURRENT_TIMESTAMP) RETURNING id",
                    Long.class,
                    identityKey
                )
        );
        provisioningService.provision(tenantCode);
    }

    @AfterEach
    void dropTenant() {
        try {
            provisioningService.dropSchema(tenantCode);
        } finally {
            transactionExecutor.execute(
                TenantSchemaNameResolver.DEFAULT_SCHEMA,
                () -> jdbcTemplate.update("DELETE FROM public.user_identity WHERE id = ?", identityId)
            );
        }
    }

    @Test
    void collectionAndOwnedChildRemovalsKeepHistoryAndAllowReinsertion() {
        Long[] ids = transactionExecutor.execute(
            tenantCode,
            () -> {
                Media media = audited(new Media(), "Media");
                media.setStorageKey("scores/" + UUID.randomUUID() + "/" + "a".repeat(64) + ".pdf");
                media.setOriginalFilename("soft-delete-test.pdf");
                media.setMimeType("application/pdf");
                media.setFileExtension("pdf");
                media.setFileSize(1);
                media.setSha256("a".repeat(64));
                media.setStatus(MediaAssetStatus.READY);
                entityManager.persist(media);
                Instruments instrument = audited(new Instruments(), "Instrument");
                entityManager.persist(instrument);

                Tracks track = audited(new Tracks(), "Track");
                track.getType().add("ORIGINAL");
                SheetsMusic score = new SheetsMusic();
                score.getMedia().add(media);
                score.getInstruments().add(instrument);
                track.getScores().add(score);
                entityManager.persist(track);

                Albums album = audited(new Albums(), "Album");
                album.getTracks().add(track);
                entityManager.persist(album);
                entityManager.flush();
                return new Long[] { album.getId(), track.getId(), score.getId(), media.getId(), instrument.getId() };
            }
        );

        transactionExecutor.execute(
            tenantCode,
            () -> {
                Albums album = entityManager.find(Albums.class, ids[0]);
                Tracks track = entityManager.find(Tracks.class, ids[1]);
                album.getTracks().clear();
                track.getType().clear();
                SheetsMusic score = track.getScores().get(0);
                score.getMedia().clear();
                score.getInstruments().clear();
                entityManager.flush();
            }
        );

        String schema = quote(schemaNameResolver.resolve(tenantCode));
        assertCounts(schema, "album_track", "album_id", ids[0], 0L, 1L);
        assertCounts(schema, "track_type", "track_id", ids[1], 0L, 1L);
        assertCounts(schema, "sheet_music_media", "sheet_music_id", ids[2], 0L, 1L);
        assertCounts(schema, "sheet_music_instrument", "sheet_music_id", ids[2], 0L, 1L);

        transactionExecutor.execute(
            tenantCode,
            () -> {
                Albums album = entityManager.find(Albums.class, ids[0]);
                Tracks track = entityManager.find(Tracks.class, ids[1]);
                album.getTracks().add(track);
                track.getType().add("ORIGINAL");
                SheetsMusic score = track.getScores().get(0);
                score.getMedia().add(entityManager.getReference(Media.class, ids[3]));
                score.getInstruments().add(entityManager.getReference(Instruments.class, ids[4]));
                entityManager.flush();
            }
        );

        assertCounts(schema, "album_track", "album_id", ids[0], 1L, 1L);
        assertCounts(schema, "track_type", "track_id", ids[1], 1L, 1L);
        assertCounts(schema, "sheet_music_media", "sheet_music_id", ids[2], 1L, 1L);
        assertCounts(schema, "sheet_music_instrument", "sheet_music_id", ids[2], 1L, 1L);

        Long replacementScoreId = transactionExecutor.execute(
            tenantCode,
            () -> {
                Tracks track = entityManager.find(Tracks.class, ids[1]);
                track.getScores().clear();
                entityManager.flush();
                SheetsMusic replacement = new SheetsMusic();
                replacement.getMedia().add(entityManager.getReference(Media.class, ids[3]));
                replacement.getInstruments().add(entityManager.getReference(Instruments.class, ids[4]));
                track.getScores().add(replacement);
                entityManager.flush();
                return replacement.getId();
            }
        );

        assertCounts(schema, "sheet_music", "track_id", ids[1], 1L, 1L);
        assertCounts(schema, "sheet_music_media", "sheet_music_id", ids[2], 0L, 2L);
        assertCounts(schema, "sheet_music_instrument", "sheet_music_id", ids[2], 0L, 2L);
        assertCounts(schema, "sheet_music_media", "sheet_music_id", replacementScoreId, 1L, 0L);
        assertCounts(schema, "sheet_music_instrument", "sheet_music_id", replacementScoreId, 1L, 0L);

        transactionExecutor.execute(
            tenantCode,
            () -> {
                Albums album = entityManager.find(Albums.class, ids[0]);
                Tracks track = entityManager.find(Tracks.class, ids[1]);
                assertThat(album.getTracks()).hasSize(1);
                assertThat(track.getType()).containsExactly("ORIGINAL");
                assertThat(track.getScores()).hasSize(1);
            }
        );
    }

    @Test
    void orderedMediaCanBeRebuiltWithAdditionalPagesWithoutTransientDuplicates() {
        Long[] ids = transactionExecutor.execute(
            tenantCode,
            () -> {
                Media source = persistMedia("source");
                Media following = persistMedia("following");
                Media firstCrop = persistMedia("crop-1");
                Media secondCrop = persistMedia("crop-2");
                Tracks track = audited(new Tracks(), "Track with crops");
                track.getType().add("ORIGINAL");
                SheetsMusic score = new SheetsMusic();
                score.getMedia().add(source);
                score.getMedia().add(following);
                track.getScores().add(score);
                entityManager.persist(track);
                entityManager.flush();
                return new Long[] { score.getId(), source.getId(), following.getId(), firstCrop.getId(), secondCrop.getId() };
            }
        );

        transactionExecutor.execute(
            tenantCode,
            () -> {
                SheetsMusic score = entityManager.find(SheetsMusic.class, ids[0]);
                List<Media> replacement = List.of(
                    entityManager.getReference(Media.class, ids[3]),
                    entityManager.getReference(Media.class, ids[4]),
                    entityManager.getReference(Media.class, ids[2])
                );
                score.getMedia().clear();
                entityManager.flush();
                score.getMedia().addAll(replacement);
                entityManager.flush();
            }
        );

        String schema = quote(schemaNameResolver.resolve(tenantCode));
        List<Long> activeMedia = jdbcTemplate.queryForList(
            "SELECT media_asset_id FROM " + schema + ".sheet_music_media WHERE sheet_music_id = ? AND deleted = FALSE ORDER BY display_order",
            Long.class,
            ids[0]
        );
        assertThat(activeMedia).containsExactly(ids[3], ids[4], ids[2]);
        assertCounts(schema, "sheet_music_media", "sheet_music_id", ids[0], 3L, 2L);
    }

    @Test
    void scoreOrderCanBeChangedWithoutTransientDuplicates() {
        Long[] ids = transactionExecutor.execute(
            tenantCode,
            () -> {
                Tracks track = audited(new Tracks(), "Reordered track");
                SheetsMusic first = new SheetsMusic();
                SheetsMusic second = new SheetsMusic();
                SheetsMusic third = new SheetsMusic();
                track.getScores().addAll(List.of(first, second, third));
                entityManager.persist(track);
                entityManager.flush();
                return new Long[] { track.getId(), first.getId(), second.getId(), third.getId() };
            }
        );

        transactionExecutor.execute(
            tenantCode,
            () -> {
                Tracks track = entityManager.find(Tracks.class, ids[0]);
                assertThat(track.getScores()).hasSize(3);
                tracksRepository.moveActiveScoreOrdersToTemporaryRange(track.getId());
                SheetsMusic first = track.getScores().get(0);
                SheetsMusic second = track.getScores().get(1);
                SheetsMusic third = track.getScores().get(2);
                track.getScores().clear();
                track.getScores().addAll(List.of(third, second, first));
                entityManager.flush();

                for (int index = 0; index < track.getScores().size(); index++) {
                    assertThat(tracksRepository.restoreActiveScoreOrder(track.getId(), track.getScores().get(index).getId(), index))
                        .isEqualTo(1);
                }
                entityManager.clear();
                assertThat(entityManager.find(Tracks.class, ids[0]).getScores())
                    .extracting(SheetsMusic::getId)
                    .containsExactly(ids[3], ids[2], ids[1]);
            }
        );

        String schema = quote(schemaNameResolver.resolve(tenantCode));
        List<Long> activeScores = jdbcTemplate.queryForList(
            "SELECT id FROM " + schema + ".sheet_music WHERE track_id = ? AND deleted = FALSE ORDER BY display_order",
            Long.class,
            ids[0]
        );
        assertThat(activeScores).containsExactly(ids[3], ids[2], ids[1]);
    }

    @Test
    void parentSoftDeleteCascadesToOwnedRowsAssociationsAndPendingWork() {
        transactionExecutor.execute(tenantCode, () -> verifyParentSoftDeleteCascades());
    }

    private void verifyParentSoftDeleteCascades() {
        String schema = quote(schemaNameResolver.resolve(tenantCode));
        Long mediaId = insertReturningId(
            "INSERT INTO " + schema + ".media_asset " +
            "(name, storage_key, original_filename, mime_type, file_extension, file_size, sha256, status, insert_by, edit_by) " +
            "VALUES ('Cascade media', 'scores/cascade/" + "b".repeat(64) + ".pdf', 'cascade-media.pdf', " +
            "'application/pdf', 'pdf', 1, '" + "b".repeat(64) + "', 'READY', 'test', 'test') RETURNING id"
        );
        Long instrumentId = insertReturningId(
            "INSERT INTO " + schema + ".instrument (name, insert_by, edit_by) VALUES ('Cascade instrument', 'test', 'test') RETURNING id"
        );
        Long trackId = insertReturningId(
            "INSERT INTO " + schema + ".track (name, insert_by, edit_by) VALUES ('Cascade track', 'test', 'test') RETURNING id"
        );
        jdbcTemplate.update("INSERT INTO " + schema + ".track_type (track_id, type) VALUES (?, 'ORIGINAL')", trackId);
        Long scoreId = insertReturningId(
            "INSERT INTO " + schema + ".sheet_music (track_id, display_order) VALUES (?, 0) RETURNING id",
            trackId
        );
        jdbcTemplate.update(
            "INSERT INTO " + schema + ".sheet_music_media (sheet_music_id, media_asset_id, display_order) VALUES (?, ?, 0)",
            scoreId,
            mediaId
        );
        jdbcTemplate.update(
            "INSERT INTO " + schema + ".sheet_music_instrument (sheet_music_id, instrument_id, display_order) VALUES (?, ?, 0)",
            scoreId,
            instrumentId
        );
        Long albumId = insertReturningId(
            "INSERT INTO " + schema + ".album (name, insert_by, edit_by) VALUES ('Cascade album', 'test', 'test') RETURNING id"
        );
        jdbcTemplate.update(
            "INSERT INTO " + schema + ".album_track (album_id, track_id, display_order) VALUES (?, ?, 0)",
            albumId,
            trackId
        );
        Long eventId = insertReturningId(
            "INSERT INTO " + schema + ".calendar_event (name, start_date, end_date, insert_by, edit_by) " +
            "VALUES ('Cascade event', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '1 hour', 'test', 'test') RETURNING id"
        );
        Long costId = insertReturningId(
            "INSERT INTO " + schema + ".calendar_event_cost (event_id, description, amount, display_order) " +
            "VALUES (?, 'Cost', 10, 0) RETURNING id",
            eventId
        );

        Long reminderId = insertReturningId(
            "INSERT INTO " + schema + ".push_reminders (event_id, event_name, user_id, send_at, sent) " +
            "VALUES (?, 'Cascade event', ?, ?, FALSE) RETURNING id",
            eventId,
            identityKey,
            Timestamp.from(Instant.now().plusSeconds(3600))
        );

        softDelete(schema, "track", trackId, "track-deleter");
        assertCounts(schema, "album_track", "track_id", trackId, 0L, 1L);
        assertCounts(schema, "track_type", "track_id", trackId, 0L, 1L);
        assertCounts(schema, "sheet_music", "track_id", trackId, 0L, 1L);
        assertCounts(schema, "sheet_music_media", "sheet_music_id", scoreId, 0L, 1L);
        assertCounts(schema, "sheet_music_instrument", "sheet_music_id", scoreId, 0L, 1L);

        Long secondTrackId = insertReturningId(
            "INSERT INTO " + schema + ".track (name, insert_by, edit_by) VALUES ('Other parent track', 'test', 'test') RETURNING id"
        );
        Long secondScoreId = insertReturningId(
            "INSERT INTO " + schema + ".sheet_music (track_id, display_order) VALUES (?, 0) RETURNING id",
            secondTrackId
        );
        jdbcTemplate.update(
            "INSERT INTO " + schema + ".sheet_music_media (sheet_music_id, media_asset_id, display_order) VALUES (?, ?, 0)",
            secondScoreId,
            mediaId
        );
        jdbcTemplate.update(
            "INSERT INTO " + schema + ".sheet_music_instrument (sheet_music_id, instrument_id, display_order) VALUES (?, ?, 0)",
            secondScoreId,
            instrumentId
        );
        Long secondAlbumId = insertReturningId(
            "INSERT INTO " + schema + ".album (name, insert_by, edit_by) VALUES ('Other parent album', 'test', 'test') RETURNING id"
        );
        jdbcTemplate.update(
            "INSERT INTO " + schema + ".album_track (album_id, track_id, display_order) VALUES (?, ?, 0)",
            secondAlbumId,
            secondTrackId
        );

        softDelete(schema, "album", secondAlbumId, "album-deleter");
        assertCounts(schema, "album_track", "album_id", secondAlbumId, 0L, 1L);
        softDelete(schema, "media_asset", mediaId, "media-deleter");
        assertCounts(schema, "sheet_music_media", "sheet_music_id", secondScoreId, 0L, 1L);
        softDelete(schema, "instrument", instrumentId, "instrument-deleter");
        assertCounts(schema, "sheet_music_instrument", "sheet_music_id", secondScoreId, 0L, 1L);

        softDelete(schema, "calendar_event", eventId, "event-deleter");
        assertCounts(schema, "calendar_event_cost", "event_id", eventId, 0L, 1L);
        assertRowDeleted(schema, "calendar_event_cost", costId, "event-deleter");
        assertRowDeleted(schema, "push_reminders", reminderId, "event-deleter");

        Long userId = insertReturningId(
            "INSERT INTO " + schema + ".app_user (user_identity_id, keycloak_id, name, active) VALUES (?, ?, 'Cascade user', TRUE) RETURNING id",
            identityId,
            identityKey
        );
        jdbcTemplate.update("INSERT INTO " + schema + ".app_user_role (user_id, role) VALUES (?, 'ROLE_USER')", userId);
        Long userEventId = insertReturningId(
            "INSERT INTO " + schema + ".calendar_event (name, start_date, end_date) VALUES ('User event', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '1 hour') RETURNING id"
        );
        Long availabilityId = insertReturningId(
            "INSERT INTO " + schema + ".calendar_event_availability (event_id, user_id, availability, response_date) " +
            "VALUES (?, ?, 'AVAILABLE', CURRENT_TIMESTAMP) RETURNING id",
            userEventId,
            userId
        );
        Long presenceId = insertReturningId(
            "INSERT INTO " + schema + ".calendar_event_presence (event_id, user_id, display_order) VALUES (?, ?, 0) RETURNING id",
            userEventId,
            userId
        );
        Long uploadMediaId = insertReturningId(
            "INSERT INTO " + schema + ".media_asset " +
            "(name, storage_key, original_filename, mime_type, file_extension, file_size, sha256, status, insert_by, edit_by) " +
            "VALUES ('Upload', 'uploads/cascade/" + "c".repeat(64) + ".pdf', 'upload.pdf', 'application/pdf', 'pdf', 1, '" +
            "c".repeat(64) + "', 'READY', 'test', 'test') RETURNING id"
        );
        Long uploadId = insertReturningId(
            "INSERT INTO " + schema + ".upload_job (user_id, name, source_media_asset_id, status) VALUES (?, 'Upload', ?, 'TO_PROCESS') RETURNING id",
            userId,
            uploadMediaId
        );
        Long userReminderId = insertReturningId(
            "INSERT INTO " + schema + ".push_reminders (event_id, event_name, user_id, send_at, sent) " +
            "VALUES (?, 'User event', ?, ?, FALSE) RETURNING id",
            userEventId,
            identityKey,
            Timestamp.from(Instant.now().plusSeconds(3600))
        );

        softDelete(schema, "app_user", userId, "user-deleter");
        assertCounts(schema, "app_user_role", "user_id", userId, 0L, 1L);
        assertRowDeleted(schema, "calendar_event_availability", availabilityId, "user-deleter");
        assertRowDeleted(schema, "calendar_event_presence", presenceId, "user-deleter");
        assertRowDeleted(schema, "upload_job", uploadId, "user-deleter");
        assertRowDeleted(schema, "push_reminders", userReminderId, "user-deleter");

        Long itemId = insertReturningId(
            "INSERT INTO " + schema + ".inventory_item " +
            "(inventory_number, name, total_quantity, condition_status, insert_by, edit_by) " +
            "VALUES ('CASCADE-1', 'Cascade item', 1, 'GOOD', 'test', 'test') RETURNING id"
        );
        Long photoMediaId = insertReturningId(
            "INSERT INTO " + schema + ".media_asset " +
            "(name, storage_key, original_filename, mime_type, file_extension, file_size, sha256, status, insert_by, edit_by) " +
            "VALUES ('photo.jpg', 'inventory/cascade/" + "d".repeat(64) + ".jpg', 'photo.jpg', 'image/jpeg', 'jpg', 1, '" +
            "d".repeat(64) + "', 'READY', 'test', 'test') RETURNING id"
        );
        Long photoId = insertReturningId(
            "INSERT INTO " + schema + ".inventory_item_photo " +
            "(item_id, media_asset_id, display_order, insert_by, edit_by, preview) " +
            "VALUES (?, ?, 0, 'test', 'test', TRUE) RETURNING id",
            itemId,
            photoMediaId
        );
        softDelete(schema, "inventory_item", itemId, "inventory-deleter");
        assertRowDeleted(schema, "inventory_item_photo", photoId, "inventory-deleter");
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

    private void assertCounts(String schema, String table, String ownerColumn, Long ownerId, long active, long deleted) {
        String sql = "SELECT COUNT(*) FROM " + schema + "." + quote(table) + " WHERE " + quote(ownerColumn) + " = ? AND deleted = ?";
        assertThat(jdbcTemplate.queryForObject(sql, Long.class, ownerId, false)).isEqualTo(active);
        assertThat(jdbcTemplate.queryForObject(sql, Long.class, ownerId, true)).isEqualTo(deleted);
    }

    private Long insertReturningId(String sql, Object... arguments) {
        return jdbcTemplate.queryForObject(sql, Long.class, arguments);
    }

    private void softDelete(String schema, String table, Long id, String actor) {
        int updated = jdbcTemplate.update(
            "UPDATE " +
            schema +
            "." +
            quote(table) +
            " SET deleted = TRUE, edit_by = ?, edit_date = CURRENT_TIMESTAMP WHERE id = ? AND deleted = FALSE",
            actor,
            id
        );
        assertThat(updated).isEqualTo(1);
    }

    private void assertRowDeleted(String schema, String table, Long id, String actor) {
        String sql = "SELECT deleted FROM " + schema + "." + quote(table) + " WHERE id = ? AND edit_by = ?";
        assertThat(jdbcTemplate.queryForObject(sql, Boolean.class, id, actor)).isTrue();
    }

    private String quote(String identifier) {
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }
}
