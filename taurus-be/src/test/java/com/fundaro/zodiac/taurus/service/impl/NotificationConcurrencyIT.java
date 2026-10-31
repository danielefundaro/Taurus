package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.IntegrationTest;
import com.fundaro.zodiac.taurus.domain.PushReminder;
import com.fundaro.zodiac.taurus.domain.UserIdentity;
import com.fundaro.zodiac.taurus.domain.Users;
import com.fundaro.zodiac.taurus.domain.enumeration.TenantFeature;
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
import com.fundaro.zodiac.taurus.repository.notification.NotificationOutboxRepository;
import com.fundaro.zodiac.taurus.repository.notification.NotificationProfileRepository;
import com.fundaro.zodiac.taurus.repository.notification.NotificationPushDeliveryRepository;
import com.fundaro.zodiac.taurus.service.NotificationPreferencesService;
import com.fundaro.zodiac.taurus.service.NotificationPushDeliveryService;
import com.fundaro.zodiac.taurus.service.PushReminderDeliveryService;
import com.fundaro.zodiac.taurus.service.PushService;
import com.fundaro.zodiac.taurus.service.TenantFeatureService;
import com.fundaro.zodiac.taurus.service.dto.notification.NotificationCategoryPreferenceDTO;
import com.fundaro.zodiac.taurus.service.dto.notification.NotificationPreferencesDTO;
import com.fundaro.zodiac.taurus.service.dto.notification.NotificationQuietHoursDTO;
import com.fundaro.zodiac.taurus.service.notification.NotificationAudience;
import com.fundaro.zodiac.taurus.service.notification.NotificationCommand;
import com.fundaro.zodiac.taurus.service.notification.PushDeliveryResult;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.LongConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Qualifica le tre code delle notifiche quando più worker prendono lo stesso job, come avviene con
 * più repliche dell'applicazione: la presa in carico è un lock pessimistico sulla riga più il
 * controllo dello stato nella stessa transazione, quindi un solo worker consegna e gli altri si
 * ritirano senza rifare il lavoro.
 *
 * <p>Il runbook di test chiede di qualificare gli scheduler multi-replica. {@code
 * UploadJobConcurrencyIT} copre la coda degli upload, dove la presa in carico è un UPDATE
 * condizionato sullo stato; qui sono coperti l'outbox delle notifiche, la coda delle consegne push e
 * i promemoria degli eventi, dove passa da {@code findByIdForUpdate}.
 */
@IntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Timeout(value = 2, unit = TimeUnit.MINUTES)
@TestPropertySource(
    properties = {
        "application.base-path=D:/data",
        "spring.liquibase.contexts=test",
        "spring.datasource.hikari.maximum-pool-size=10",
        "spring.datasource.hikari.minimum-idle=2",
        "spring.security.oauth2.client.registration.oidc.client-id=test",
        "spring.security.oauth2.client.registration.oidc.client-secret=test",
        // Gli scheduler reali ruberebbero i job al test: qui i worker sono i thread della prova.
        "application.notifications.scheduler-enabled=false",
        "application.notification-push-delivery.scheduler-enabled=false",
    }
)
class NotificationConcurrencyIT {

    private static final String SUBJECT = "concurrency-user-1";
    private static final int WORKERS = 6;

    @MockBean
    ClientRegistrationRepository clientRegistrationRepository;
    @MockBean
    JwtDecoder jwtDecoder;
    @MockBean
    TenantFeatureService tenantFeatureService;
    @MockBean
    PushService pushService;
    @Autowired
    TenantSchemaProvisioningService provisioningService;
    @Autowired
    TenantTransactionExecutor transactionExecutor;
    @Autowired
    NotificationOutboxPublisher publisher;
    @Autowired
    NotificationDispatcher dispatcher;
    @Autowired
    NotificationPreferencesService preferencesService;
    @Autowired
    NotificationPushDeliveryService pushDeliveryService;
    @Autowired
    PushReminderDeliveryService reminderDeliveryService;
    @Autowired
    NotificationOutboxRepository outboxRepository;
    @Autowired
    NotificationPushDeliveryRepository pushDeliveryRepository;
    @Autowired
    NotificationProfileRepository profileRepository;
    @Autowired
    PushReminderRepository reminderRepository;
    @Autowired
    NoticesRepository noticesRepository;
    @Autowired
    UsersRepository usersRepository;
    @PersistenceContext
    EntityManager entityManager;

    private final String tenant = "notification-concurrency-" + UUID.randomUUID();

    @BeforeAll
    void provisionTenant() {
        provisioningService.provision(tenant);
    }

    @BeforeEach
    void prepare() {
        when(tenantFeatureService.isEnabled(any(TenantFeature.class))).thenReturn(true);
        when(pushService.sendToUserNow(anyString(), anyString(), anyString(), anyString(), any()))
            .thenReturn(new PushDeliveryResult(1, 0, 0, 0, 1));
        transactionExecutor.execute(tenant, () -> {
            if (usersRepository.findByKeycloakIdAndDeletedFalse(SUBJECT).isEmpty()) createUser();
        });
    }

    @AfterEach
    void cleanTenantData() {
        // deleteAllInBatch: le righe arrivano dalle transazioni dei worker, quindi una cancellazione
        // entità per entità fallirebbe sul lock ottimistico.
        transactionExecutor.execute(tenant, () -> {
            pushDeliveryRepository.deleteAllInBatch();
            reminderRepository.deleteAllInBatch();
            noticesRepository.deleteAllInBatch();
            outboxRepository.deleteAllInBatch();
            profileRepository.deleteAllInBatch();
            entityManager.createNativeQuery("DELETE FROM calendar_event").executeUpdate();
        });
    }

    @AfterAll
    void dropTenant() {
        provisioningService.dropSchema(tenant);
    }

    /** L'outbox: sei worker sullo stesso evento producono un solo avviso e un solo job push. */
    @Test
    void deliversAnOutboxEventOnlyOnce() throws Exception {
        transactionExecutor.execute(tenant, () ->
            preferencesService.save(preferences(NotificationPushMode.IMMEDIATE), authentication())
        );
        long eventId = enqueue(command("concurrent-outbox-" + UUID.randomUUID()));

        inParallel(id -> dispatcher.dispatch(id), eventId);

        transactionExecutor.execute(tenant, () -> {
            assertThat(outboxRepository.findById(eventId)).get()
                .extracting(event -> event.getStatus()).isEqualTo(NotificationStatus.DELIVERED);
            assertThat(noticesRepository.findAll()).hasSize(1);
            assertThat(pushDeliveryRepository.findAll()).hasSize(1);
        });
    }

    /** La coda push: la notifica parte una volta sola, non sei. */
    @Test
    void sendsAPushDeliveryOnlyOnce() throws Exception {
        transactionExecutor.execute(tenant, () ->
            preferencesService.save(preferences(NotificationPushMode.IMMEDIATE), authentication())
        );
        long jobId = transactionExecutor.execute(tenant, () -> pushDeliveryRepository.save(pushJob()).getId());

        inParallel(id -> pushDeliveryService.process(id), jobId);

        verify(pushService, times(1)).sendToUserNow(anyString(), anyString(), anyString(), anyString(), any());
        transactionExecutor.execute(tenant, () ->
            assertThat(pushDeliveryRepository.findById(jobId)).get()
                .extracting(job -> job.getStatus()).isEqualTo(NotificationStatus.DELIVERED)
        );
    }

    /** I promemoria degli eventi: un solo invio per promemoria, con sei worker insieme. */
    @Test
    void sendsAnEventReminderOnlyOnce() throws Exception {
        long reminderId = transactionExecutor.execute(tenant, () -> {
            Long eventId = createCalendarEvent();
            return reminderRepository.save(reminder(eventId)).getId();
        });

        inParallel(id -> reminderDeliveryService.process(id), reminderId);

        verify(pushService, times(1)).sendToUserNow(anyString(), anyString(), anyString(), anyString(), any());
        transactionExecutor.execute(tenant, () ->
            assertThat(reminderRepository.findById(reminderId)).get()
                .extracting(value -> value.getStatus()).isEqualTo(NotificationStatus.DELIVERED)
        );
    }

    /** Esegue il job da più thread che partono insieme, come repliche diverse dell'applicazione. */
    private void inParallel(LongConsumer job, long id) throws Exception {
        ExecutorService workers = Executors.newFixedThreadPool(WORKERS);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int worker = 0; worker < WORKERS; worker++) {
                futures.add(workers.submit(() -> {
                    start.await();
                    transactionExecutor.execute(tenant, () -> job.accept(id));
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) future.get(60, TimeUnit.SECONDS);
        } finally {
            workers.shutdownNow();
        }
    }

    private long enqueue(NotificationCommand command) {
        return transactionExecutor.execute(tenant, () -> {
            publisher.enqueue(command);
            return outboxRepository.findAll().stream()
                .filter(event -> command.eventKey().equals(event.getEventKey()))
                .findFirst()
                .orElseThrow()
                .getId();
        });
    }

    private NotificationPushDelivery pushJob() {
        ZonedDateTime now = ZonedDateTime.now();
        NotificationPushDelivery job = new NotificationPushDelivery();
        job.initializeAudit("test");
        job.setSourceEventKey("concurrent-push-" + UUID.randomUUID());
        job.setUserId(SUBJECT);
        job.setSource(NotificationSource.INVENTORY);
        job.setDeliveryType(NotificationPushDeliveryType.IMMEDIATE);
        job.setTitle("Inventario: oggetto creato");
        job.setMessage("Mario Rossi ha creato un oggetto.");
        job.setTargetPath("/inventory");
        job.setScheduledAt(now.minusMinutes(1));
        job.setExpiresAt(now.plusHours(1));
        job.setStatus(NotificationStatus.PENDING);
        job.setNextAttemptAt(now.minusMinutes(1));
        return job;
    }

    private static PushReminder reminder(Long eventId) {
        PushReminder reminder = new PushReminder();
        reminder.initializeAudit("scheduler");
        reminder.setEventId(eventId);
        reminder.setEventName("Prova");
        reminder.setUserId(SUBJECT);
        reminder.setSendAt(Instant.now().minusSeconds(600));
        reminder.setEventStartAt(Instant.now().plusSeconds(3600));
        reminder.setStatus(NotificationStatus.PENDING);
        return reminder;
    }

    /** I promemoria hanno una FK verso l'evento: serve un'occorrenza reale nello schema del tenant. */
    private Long createCalendarEvent() {
        entityManager.createNativeQuery(
            "INSERT INTO calendar_event (name, start_date, end_date, series_exception, series_excluded, deleted, insert_by, insert_date, edit_by, edit_date)"
                + " VALUES ('Prova', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '1 hour', FALSE, FALSE, FALSE, 'test', CURRENT_TIMESTAMP, 'test', CURRENT_TIMESTAMP)"
        ).executeUpdate();
        return ((Number) entityManager.createNativeQuery("SELECT MAX(id) FROM calendar_event").getSingleResult()).longValue();
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
        user.setEmail("concurrency@example.test");
        user.setUserIdentity(identity);
        user.setDeleted(false);
        user.setInsertBy(SUBJECT);
        user.setInsertDate(new Date());
        user.setEditBy(SUBJECT);
        user.setEditDate(new Date());
        usersRepository.save(user);
    }

    private static NotificationCommand command(String eventKey) {
        return new NotificationCommand(
            eventKey,
            NotificationSource.INVENTORY,
            "InventoryItem",
            "1",
            "CREATE",
            "Inventario: oggetto creato",
            "Mario Rossi ha creato un oggetto.",
            NotificationSeverity.SUCCESS,
            NotificationPreferencePolicy.CONFIGURABLE,
            "/inventory",
            SUBJECT,
            "Mario Rossi",
            Set.of(NotificationAudience.user(SUBJECT)),
            null
        );
    }

    private static NotificationPreferencesDTO preferences(NotificationPushMode pushMode) {
        List<NotificationCategoryPreferenceDTO> categories = Arrays.stream(NotificationSource.values())
            .map(source -> new NotificationCategoryPreferenceDTO(source, true, pushMode))
            .toList();
        return new NotificationPreferencesDTO(
            null,
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
