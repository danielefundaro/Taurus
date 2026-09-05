package com.fundaro.zodiac.taurus.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.fundaro.zodiac.taurus.IntegrationTest;
import com.fundaro.zodiac.taurus.domain.Notices;
import com.fundaro.zodiac.taurus.domain.PushReminder;
import com.fundaro.zodiac.taurus.domain.UserIdentity;
import com.fundaro.zodiac.taurus.domain.Users;
import com.fundaro.zodiac.taurus.domain.enumeration.TenantFeature;
import com.fundaro.zodiac.taurus.domain.notification.NotificationDeliveryOrigin;
import com.fundaro.zodiac.taurus.domain.notification.NotificationOutbox;
import com.fundaro.zodiac.taurus.domain.notification.NotificationPreferencePolicy;
import com.fundaro.zodiac.taurus.domain.notification.NotificationPushDelivery;
import com.fundaro.zodiac.taurus.domain.notification.NotificationPushDeliveryType;
import com.fundaro.zodiac.taurus.domain.notification.NotificationPushMode;
import com.fundaro.zodiac.taurus.domain.notification.NotificationPushPreview;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSeverity;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSource;
import com.fundaro.zodiac.taurus.domain.notification.NotificationStatus;
import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaProvisioningService;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import com.fundaro.zodiac.taurus.repository.NoticesRepository;
import com.fundaro.zodiac.taurus.repository.PushReminderRepository;
import com.fundaro.zodiac.taurus.repository.UsersRepository;
import com.fundaro.zodiac.taurus.repository.notification.NotificationDeliveryAdminQueryRepository;
import com.fundaro.zodiac.taurus.repository.notification.NotificationDeliveryAdminQueryRepository.NotificationDeliveryFilter;
import com.fundaro.zodiac.taurus.repository.notification.NotificationOutboxRepository;
import com.fundaro.zodiac.taurus.repository.notification.NotificationProfileRepository;
import com.fundaro.zodiac.taurus.repository.notification.NotificationPushDeliveryRepository;
import com.fundaro.zodiac.taurus.service.NotificationDeliveryAdminService;
import com.fundaro.zodiac.taurus.service.NotificationPreferencesService;
import com.fundaro.zodiac.taurus.service.TenantFeatureService;
import com.fundaro.zodiac.taurus.service.dto.notification.NotificationCategoryPreferenceDTO;
import com.fundaro.zodiac.taurus.service.dto.notification.NotificationDeliveryRef;
import com.fundaro.zodiac.taurus.service.dto.notification.NotificationPreferencesDTO;
import com.fundaro.zodiac.taurus.service.dto.notification.NotificationQuietHoursDTO;
import com.fundaro.zodiac.taurus.service.notification.NotificationAudience;
import com.fundaro.zodiac.taurus.service.notification.NotificationCommand;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.TestPropertySource;

/**
 * Copre il percorso end-to-end delle preferenze: profilo per tenant, fan-out
 * guidato dalle preferenze e lettura unificata della console amministrativa.
 */
@IntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
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
class NotificationPreferencesIT {

    private static final String SUBJECT = "preferences-user-1";

    @MockBean ClientRegistrationRepository clientRegistrationRepository;
    @MockBean JwtDecoder jwtDecoder;
    @MockBean NotificationScheduler notificationScheduler;
    @MockBean NotificationPushScheduler notificationPushScheduler;
    @MockBean TenantFeatureService tenantFeatureService;
    @Autowired TenantSchemaProvisioningService provisioningService;
    @Autowired TenantTransactionExecutor transactionExecutor;
    @Autowired NotificationOutboxPublisher publisher;
    @Autowired NotificationDispatcher dispatcher;
    @Autowired NotificationPreferencesService preferencesService;
    @Autowired NotificationDeliveryAdminService adminService;
    @Autowired NotificationDeliveryAdminQueryRepository adminQueryRepository;
    @Autowired NotificationOutboxRepository outboxRepository;
    @Autowired NotificationPushDeliveryRepository pushDeliveryRepository;
    @Autowired NotificationProfileRepository profileRepository;
    @Autowired PushReminderRepository reminderRepository;
    @Autowired NoticesRepository noticesRepository;
    @Autowired UsersRepository usersRepository;
    @PersistenceContext EntityManager entityManager;

    private final String tenantOne = "preferences-a-" + UUID.randomUUID();
    private final String tenantTwo = "preferences-b-" + UUID.randomUUID();

    @BeforeAll
    void provisionTenants() {
        provisioningService.provision(tenantOne);
        provisioningService.provision(tenantTwo);
    }

    @BeforeEach
    void prepare() {
        when(tenantFeatureService.isEnabled(TenantFeature.FINANCE)).thenReturn(true);
        when(tenantFeatureService.isEnabled(TenantFeature.INVENTORY)).thenReturn(true);
        for (String tenant : List.of(tenantOne, tenantTwo)) {
            transactionExecutor.execute(tenant, () -> {
                if (usersRepository.findByKeycloakIdAndDeletedFalse(SUBJECT).isEmpty()) createUser();
            });
        }
    }

    @AfterEach
    void cleanTenantData() {
        for (String tenant : List.of(tenantOne, tenantTwo)) {
            // deleteAllInBatch: le righe sono state aggiornate da transazioni precedenti,
            // quindi un delete entity-by-entity fallirebbe sul lock ottimistico.
            transactionExecutor.execute(tenant, () -> {
                pushDeliveryRepository.deleteAllInBatch();
                reminderRepository.deleteAllInBatch();
                noticesRepository.deleteAllInBatch();
                outboxRepository.deleteAllInBatch();
                profileRepository.deleteAllInBatch();
                entityManager.createNativeQuery("DELETE FROM calendar_event").executeUpdate();
            });
        }
    }

    @AfterAll
    void dropTenants() {
        provisioningService.dropSchema(tenantOne);
        provisioningService.dropSchema(tenantTwo);
    }

    @Test
    void readsCompleteDefaultsWithoutMaterialisingAProfile() {
        transactionExecutor.execute(tenantOne, () -> {
            NotificationPreferencesDTO defaults = preferencesService.get(authentication());

            assertThat(defaults.version()).isNull();
            assertThat(defaults.categories()).hasSize(NotificationSource.values().length);
            assertThat(profileRepository.findByUserKeycloakIdAndDeletedFalse(SUBJECT)).isEmpty();
        });
    }

    @Test
    void materialisesTheProfileOnTheFirstPutAndRejectsAStaleVersion() {
        transactionExecutor.execute(tenantOne, () -> {
            NotificationPreferencesDTO saved = preferencesService.save(preferences(null, NotificationPushMode.IMMEDIATE, true), authentication());

            assertThat(saved.version()).isNotNull();
            assertThat(profileRepository.findByUserKeycloakIdAndDeletedFalse(SUBJECT)).isPresent();
        });
        assertThatThrownBy(() -> transactionExecutor.execute(tenantOne, () ->
            preferencesService.save(preferences(99L, NotificationPushMode.IMMEDIATE, true), authentication())
        )).isInstanceOf(RequestAlertException.class)
            .extracting(error -> ((RequestAlertException) error).getErrorKey())
            .isEqualTo("preferences.versionConflict");
    }

    @Test
    void keepsPreferencesIsolatedBetweenTenantsForTheSameSubject() {
        transactionExecutor.execute(tenantOne, () ->
            preferencesService.save(preferences(null, NotificationPushMode.DAILY_DIGEST, false), authentication())
        );

        transactionExecutor.execute(tenantTwo, () -> {
            NotificationPreferencesDTO other = preferencesService.get(authentication());
            assertThat(other.version()).isNull();
            assertThat(other.categories()).allSatisfy(category -> {
                assertThat(category.inAppEnabled()).isTrue();
                assertThat(category.pushMode()).isEqualTo(NotificationPushMode.OFF);
            });
        });
    }

    @Test
    void suppressesTheInAppRowAndStillEnqueuesThePushWhenTheCategoryIsDisabled() {
        transactionExecutor.execute(tenantOne, () ->
            preferencesService.save(preferences(null, NotificationPushMode.IMMEDIATE, false), authentication())
        );
        long eventId = enqueue(tenantOne, command("suppressed-event", NotificationPreferencePolicy.CONFIGURABLE));

        transactionExecutor.execute(tenantOne, () -> dispatcher.dispatch(eventId));

        transactionExecutor.execute(tenantOne, () -> {
            assertThat(noticesRepository.findAll()).isEmpty();
            assertThat(pushDeliveryRepository.findAll()).hasSize(1)
                .allSatisfy(job -> assertThat(job.getDeliveryType()).isEqualTo(NotificationPushDeliveryType.IMMEDIATE));
            // Un evento interamente soppresso non resta comunque bloccato nell'outbox.
            assertThat(outboxRepository.findById(eventId)).get()
                .extracting(NotificationOutbox::getStatus).isEqualTo(NotificationStatus.DELIVERED);
        });
    }

    @Test
    void keepsARequiredEventInAppEvenWithTheCategoryDisabledAndWithoutForcingPush() {
        transactionExecutor.execute(tenantOne, () ->
            preferencesService.save(preferences(null, NotificationPushMode.OFF, false), authentication())
        );
        long eventId = enqueue(tenantOne, command("required-event", NotificationPreferencePolicy.REQUIRED));

        transactionExecutor.execute(tenantOne, () -> dispatcher.dispatch(eventId));

        transactionExecutor.execute(tenantOne, () -> {
            assertThat(noticesRepository.findAll()).hasSize(1)
                .allSatisfy(notice -> assertThat(notice.getPreferencePolicy()).isEqualTo(NotificationPreferencePolicy.REQUIRED));
            assertThat(pushDeliveryRepository.findAll()).isEmpty();
        });
    }

    @Test
    void marksTheOutboxDeliveredWhenEveryChannelIsSuppressed() {
        transactionExecutor.execute(tenantOne, () ->
            preferencesService.save(preferences(null, NotificationPushMode.OFF, false), authentication())
        );
        long eventId = enqueue(tenantOne, command("fully-suppressed", NotificationPreferencePolicy.CONFIGURABLE));

        transactionExecutor.execute(tenantOne, () -> dispatcher.dispatch(eventId));

        transactionExecutor.execute(tenantOne, () -> {
            assertThat(noticesRepository.findAll()).isEmpty();
            assertThat(pushDeliveryRepository.findAll()).isEmpty();
            assertThat(outboxRepository.findById(eventId)).get()
                .extracting(NotificationOutbox::getStatus).isEqualTo(NotificationStatus.DELIVERED);
        });
    }

    @Test
    void stampsTheDigestBucketAndDoesNotDuplicateJobsOnRetry() {
        transactionExecutor.execute(tenantOne, () ->
            preferencesService.save(preferences(null, NotificationPushMode.DAILY_DIGEST, true), authentication())
        );
        long eventId = enqueue(tenantOne, command("digest-event", NotificationPreferencePolicy.CONFIGURABLE));

        transactionExecutor.execute(tenantOne, () -> dispatcher.dispatch(eventId));
        transactionExecutor.execute(tenantOne, () -> dispatcher.dispatch(eventId));

        transactionExecutor.execute(tenantOne, () -> {
            assertThat(noticesRepository.findAll()).hasSize(1);
            List<NotificationPushDelivery> jobs = pushDeliveryRepository.findAll();
            assertThat(jobs).hasSize(1);
            assertThat(jobs.get(0).getDeliveryType()).isEqualTo(NotificationPushDeliveryType.DIGEST_ITEM);
            assertThat(jobs.get(0).getDigestLocalDate()).isNotNull();
        });
    }

    @Test
    void listsFailuresFromAllThreeOriginsAndFiltersThem() {
        transactionExecutor.execute(tenantOne, () -> {
            outboxRepository.save(failedOutbox("console-outbox"));
            pushDeliveryRepository.save(failedPush("console-push"));
            reminderRepository.save(failedReminder(createCalendarEvent()));
        });

        transactionExecutor.execute(tenantOne, () -> {
            var all = adminService.find(NotificationStatus.FAILED, null, null, null, null, null, 0, 20, "occurredAt,asc");
            assertThat(all.getTotalElements()).isEqualTo(3);
            assertThat(all.getContent()).extracting(dto -> dto.origin())
                .containsExactlyInAnyOrder(
                    NotificationDeliveryOrigin.OUTBOX,
                    NotificationDeliveryOrigin.PUSH,
                    NotificationDeliveryOrigin.REMINDER
                );
            assertThat(all.getContent()).allSatisfy(dto -> assertThat(dto.eventKeyHash()).hasSize(16));

            var pushOnly = adminService.find(
                NotificationStatus.FAILED, NotificationDeliveryOrigin.PUSH, null, null, null, null, 0, 20, "occurredAt,asc"
            );
            assertThat(pushOnly.getTotalElements()).isEqualTo(1);
            assertThat(pushOnly.getContent().get(0).deliveryType()).isEqualTo("IMMEDIATE");

            var calendarOnly = adminService.find(
                NotificationStatus.FAILED, null, NotificationSource.CALENDAR, null, null, null, 0, 20, "occurredAt,asc"
            );
            assertThat(calendarOnly.getTotalElements()).isEqualTo(1);
            assertThat(calendarOnly.getContent().get(0).origin()).isEqualTo(NotificationDeliveryOrigin.REMINDER);

            var byOperation = adminService.find(
                NotificationStatus.FAILED, null, null, "CREATE", null, null, 0, 20, "occurredAt,asc"
            );
            assertThat(byOperation.getTotalElements()).isEqualTo(1);
            assertThat(byOperation.getContent().get(0).origin()).isEqualTo(NotificationDeliveryOrigin.OUTBOX);

            var future = adminService.find(
                NotificationStatus.FAILED, null, null, null, ZonedDateTime.now().plusDays(1), null, 0, 20, "occurredAt,asc"
            );
            assertThat(future.getTotalElements()).isZero();
        });
    }

    @Test
    void paginatesTheUnifiedListingDeterministically() {
        transactionExecutor.execute(tenantOne, () -> {
            outboxRepository.save(failedOutbox("page-outbox"));
            pushDeliveryRepository.save(failedPush("page-push"));
            reminderRepository.save(failedReminder(createCalendarEvent()));
        });

        transactionExecutor.execute(tenantOne, () -> {
            var first = adminService.find(NotificationStatus.FAILED, null, null, null, null, null, 0, 2, "id,asc");
            var second = adminService.find(NotificationStatus.FAILED, null, null, null, null, null, 1, 2, "id,asc");

            assertThat(first.getContent()).hasSize(2);
            assertThat(second.getContent()).hasSize(1);
            assertThat(first.getTotalElements()).isEqualTo(3);
            assertThat(first.getContent()).extracting(dto -> dto.rowKey())
                .doesNotContainAnyElementsOf(second.getContent().stream().map(dto -> dto.rowKey()).toList());
        });
    }

    @Test
    void retriesAndClosesPushJobsThroughTheConsole() {
        transactionExecutor.execute(tenantOne, () -> pushDeliveryRepository.save(failedPush("console-retry")));

        long jobId = transactionExecutor.execute(tenantOne, () -> pushDeliveryRepository.findAll().get(0).getId());

        transactionExecutor.execute(tenantOne, () ->
            adminService.retry(List.of(new NotificationDeliveryRef(NotificationDeliveryOrigin.PUSH, jobId)), authentication())
        );
        transactionExecutor.execute(tenantOne, () -> {
            NotificationPushDelivery job = pushDeliveryRepository.findAll().get(0);
            assertThat(job.getStatus()).isEqualTo(NotificationStatus.PENDING);
            assertThat(job.getAttempts()).isZero();
            assertThat(job.getExpiresAt()).isAfter(ZonedDateTime.now());
        });

        transactionExecutor.execute(tenantOne, () ->
            adminService.close(NotificationDeliveryOrigin.PUSH, jobId, "MANUAL_CLOSE", authentication())
        );
        transactionExecutor.execute(tenantOne, () -> {
            NotificationPushDelivery job = pushDeliveryRepository.findAll().get(0);
            assertThat(job.getStatus()).isEqualTo(NotificationStatus.SKIPPED);
            assertThat(job.getSkipReason()).isEqualTo("MANUAL_CLOSE");
        });
    }

    @Test
    void aggregatesTheDashboardTotalOverEveryOrigin() {
        transactionExecutor.execute(tenantOne, () -> {
            outboxRepository.save(failedOutbox("summary-outbox"));
            pushDeliveryRepository.save(failedPush("summary-push"));
            reminderRepository.save(failedReminder(createCalendarEvent()));
        });

        transactionExecutor.execute(tenantOne, () -> {
            var summary = adminQueryRepository.summarize(NotificationStatus.FAILED);
            assertThat(summary.failureCount()).isEqualTo(3);
            assertThat(summary.oldestOccurredAt()).isNotNull();
        });
    }

    @Test
    void countsOnlyTheCurrentTenantRowsInTheConsole() {
        transactionExecutor.execute(tenantOne, () -> outboxRepository.save(failedOutbox("tenant-a-event")));

        transactionExecutor.execute(tenantTwo, () -> {
            var filter = new NotificationDeliveryFilter(NotificationStatus.FAILED, null, null, null, null, null);
            assertThat(adminQueryRepository.count(filter)).isZero();
        });
    }

    private void createUser() {
        // user_identity vive nello schema public: è condivisa tra i tenant e va riusata.
        UserIdentity identity = entityManager
            .createQuery("select identity from UserIdentity identity where identity.keycloakId = :subject", UserIdentity.class)
            .setParameter("subject", SUBJECT)
            .getResultStream()
            .findFirst()
            .orElse(null);
        if (identity == null) {
            identity = new UserIdentity();
            identity.setKeycloakId(SUBJECT);
            identity.initializeAudit(SUBJECT);
            entityManager.persist(identity);
        }
        Users user = new Users();
        user.setKeycloakId(SUBJECT);
        user.setActive(true);
        user.setName("Mario");
        user.setLastName("Rossi");
        user.setEmail("preferences@example.test");
        user.setUserIdentity(identity);
        user.setDeleted(false);
        user.setInsertBy(SUBJECT);
        user.setInsertDate(new java.util.Date());
        user.setEditBy(SUBJECT);
        user.setEditDate(new java.util.Date());
        usersRepository.save(user);
    }

    private long enqueue(String tenant, NotificationCommand command) {
        return transactionExecutor.execute(tenant, () -> {
            publisher.enqueue(command);
            return outboxRepository.findAll().stream()
                .filter(event -> command.eventKey().equals(event.getEventKey()))
                .findFirst()
                .orElseThrow()
                .getId();
        });
    }

    private static NotificationCommand command(String eventKey, NotificationPreferencePolicy policy) {
        return new NotificationCommand(
            eventKey,
            NotificationSource.INVENTORY,
            "InventoryItem",
            "1",
            "CREATE",
            "Inventario: oggetto creato",
            "Mario Rossi ha creato un oggetto.",
            NotificationSeverity.SUCCESS,
            policy,
            "/inventory",
            SUBJECT,
            "Mario Rossi",
            Set.of(NotificationAudience.user(SUBJECT)),
            null
        );
    }

    private static NotificationOutbox failedOutbox(String eventKey) {
        NotificationOutbox event = new NotificationOutbox();
        event.initializeAudit("dispatcher");
        event.setEventKey(eventKey);
        event.setSource(NotificationSource.INVENTORY);
        event.setAggregateType("InventoryItem");
        event.setOperation("CREATE");
        event.setTitle("Titolo");
        event.setMessage("Messaggio");
        event.setActorId(SUBJECT);
        event.setActorDisplayName("Mario Rossi");
        event.setOccurredAt(ZonedDateTime.now().minusHours(3));
        event.setStatus(NotificationStatus.FAILED);
        event.setAttempts(8);
        event.setNextAttemptAt(ZonedDateTime.now().minusHours(2));
        event.setLastError("java.lang.IllegalStateException: unavailable");
        return event;
    }

    private static NotificationPushDelivery failedPush(String eventKey) {
        NotificationPushDelivery delivery = new NotificationPushDelivery();
        delivery.initializeAudit("dispatcher");
        delivery.setSourceEventKey(eventKey);
        delivery.setUserId(SUBJECT);
        delivery.setSource(NotificationSource.INVENTORY);
        delivery.setDeliveryType(NotificationPushDeliveryType.IMMEDIATE);
        delivery.setTitle("Titolo");
        delivery.setMessage("Messaggio");
        delivery.setTargetPath("/inventory");
        delivery.setScheduledAt(ZonedDateTime.now().minusHours(2));
        delivery.setExpiresAt(ZonedDateTime.now().minusHours(1));
        delivery.setStatus(NotificationStatus.FAILED);
        delivery.setAttempts(8);
        delivery.setLastError("PERMANENT_PROVIDER_FAILURE");
        return delivery;
    }

    /** I promemoria hanno una FK verso l'evento: serve un'occorrenza reale nello schema del tenant. */
    private Long createCalendarEvent() {
        entityManager.createNativeQuery(
            "INSERT INTO calendar_event (name, start_date, end_date, series_exception, series_excluded, deleted, insert_by, insert_date, edit_by, edit_date)"
            + " VALUES ('Prova', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '1 hour', FALSE, FALSE, FALSE, 'test', CURRENT_TIMESTAMP, 'test', CURRENT_TIMESTAMP)"
        ).executeUpdate();
        return ((Number) entityManager.createNativeQuery("SELECT MAX(id) FROM calendar_event").getSingleResult()).longValue();
    }

    private static PushReminder failedReminder(Long eventId) {
        PushReminder reminder = new PushReminder();
        reminder.initializeAudit("scheduler");
        reminder.setEventId(eventId);
        reminder.setEventName("Prova");
        reminder.setUserId(SUBJECT);
        reminder.setSendAt(Instant.now().minusSeconds(3600));
        reminder.setEventStartAt(Instant.now().plusSeconds(3600));
        reminder.setStatus(NotificationStatus.FAILED);
        reminder.setSent(true);
        reminder.setAttempts(8);
        reminder.setLastError("PERMANENT_PROVIDER_FAILURE");
        return reminder;
    }

    private static NotificationPreferencesDTO preferences(Long version, NotificationPushMode pushMode, boolean inAppEnabled) {
        List<NotificationCategoryPreferenceDTO> categories = Arrays.stream(NotificationSource.values())
            .map(source -> new NotificationCategoryPreferenceDTO(source, inAppEnabled, pushMode))
            .toList();
        return new NotificationPreferencesDTO(
            version,
            "Europe/Rome",
            true,
            30,
            new NotificationQuietHoursDTO(false, LocalTime.of(22, 0), LocalTime.of(7, 0)),
            null,
            LocalTime.of(8, 0),
            NotificationPushPreview.PRIVATE,
            categories
        );
    }

    private static JwtAuthenticationToken authentication() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(SUBJECT).build();
        return new JwtAuthenticationToken(jwt);
    }
}
