package com.fundaro.zodiac.taurus.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.fundaro.zodiac.taurus.IntegrationTest;
import com.fundaro.zodiac.taurus.domain.Notices;
import com.fundaro.zodiac.taurus.domain.notification.NotificationOutbox;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSeverity;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSource;
import com.fundaro.zodiac.taurus.domain.notification.NotificationStatus;
import com.fundaro.zodiac.taurus.domain.enumeration.TenantFeature;
import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaProvisioningService;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import com.fundaro.zodiac.taurus.repository.NoticesRepository;
import com.fundaro.zodiac.taurus.repository.notification.NotificationOutboxRepository;
import com.fundaro.zodiac.taurus.service.NoticesService;
import com.fundaro.zodiac.taurus.service.TenantFeatureService;
import com.fundaro.zodiac.taurus.service.notification.NotificationAudience;
import com.fundaro.zodiac.taurus.service.notification.NotificationCommand;
import com.fundaro.zodiac.taurus.service.notification.NotificationDelivery;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;

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
class NotificationDeliveryIT {

    @MockBean ClientRegistrationRepository clientRegistrationRepository;
    @MockBean JwtDecoder jwtDecoder;
    @MockBean NotificationScheduler notificationScheduler;
    @MockBean TenantFeatureService tenantFeatureService;
    @Autowired TenantSchemaProvisioningService provisioningService;
    @Autowired TenantTransactionExecutor transactionExecutor;
    @Autowired NotificationOutboxPublisher publisher;
    @Autowired NotificationDispatcher dispatcher;
    @Autowired NotificationOutboxRepository outboxRepository;
    @Autowired NoticesRepository noticesRepository;
    @Autowired NoticesService noticesService;
    @Autowired DataErasureService dataErasureService;
    @PersistenceContext EntityManager entityManager;

    private final String tenantOne = "notification-delivery-a-" + UUID.randomUUID();
    private final String tenantTwo = "notification-delivery-b-" + UUID.randomUUID();

    @BeforeAll
    void provisionTenants() {
        provisioningService.provision(tenantOne);
        provisioningService.provision(tenantTwo);
    }

    @BeforeEach
    void enableTenantFeatures() {
        when(tenantFeatureService.isEnabled(TenantFeature.FINANCE)).thenReturn(true);
        when(tenantFeatureService.isEnabled(TenantFeature.INVENTORY)).thenReturn(true);
    }

    @AfterEach
    void cleanTenantData() {
        for (String tenant : List.of(tenantOne, tenantTwo)) {
            transactionExecutor.execute(tenant, () -> {
                noticesRepository.deleteAll();
                outboxRepository.deleteAll();
            });
        }
    }

    @AfterAll
    void dropTenants() {
        provisioningService.dropSchema(tenantOne);
        provisioningService.dropSchema(tenantTwo);
    }

    @Test
    void completesAPartialDeliveryWithoutDuplicatingRecipients() {
        long eventId = enqueue(tenantOne, command("partial-event", "recipient-1", "recipient-2"));
        transactionExecutor.execute(tenantOne, () -> noticesService.addNoticeToUser(delivery("partial-event", "recipient-1")));

        transactionExecutor.execute(tenantOne, () -> dispatcher.dispatch(eventId));
        transactionExecutor.execute(tenantOne, () -> dispatcher.dispatch(eventId));

        transactionExecutor.execute(tenantOne, () -> {
            assertThat(noticesRepository.findAll()).extracting(Notices::getUserId)
                .containsExactlyInAnyOrder("recipient-1", "recipient-2");
            assertThat(noticesRepository.findAll().stream().filter(notice -> "recipient-1".equals(notice.getUserId()))).hasSize(1);
            assertThat(outboxRepository.findById(eventId)).get().extracting(NotificationOutbox::getStatus)
                .isEqualTo(NotificationStatus.DELIVERED);
        });
    }

    @Test
    void pessimisticLockPreventsConcurrentDoubleDelivery() throws Exception {
        long eventId = enqueue(tenantOne, command("concurrent-event", "recipient-1"));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = executor.submit(() -> dispatchAfter(start, eventId));
            Future<?> second = executor.submit(() -> dispatchAfter(start, eventId));
            start.countDown();
            first.get();
            second.get();
        } finally {
            executor.shutdownNow();
        }

        transactionExecutor.execute(tenantOne, () -> {
            assertThat(noticesRepository.findAll()).hasSize(1);
            assertThat(outboxRepository.findById(eventId)).get().extracting(NotificationOutbox::getStatus)
                .isEqualTo(NotificationStatus.DELIVERED);
        });
    }

    @Test
    void ignoresEventsNotYetReadyAndKeepsEventsWithoutRecipientsPending() {
        long futureId = enqueue(tenantOne, command("future-event", "recipient-1"));
        transactionExecutor.execute(tenantOne, () -> {
            NotificationOutbox event = outboxRepository.findById(futureId).orElseThrow();
            event.setNextAttemptAt(ZonedDateTime.now().plusHours(1));
            outboxRepository.save(event);
        });

        assertThat(transactionExecutor.execute(tenantOne, dispatcher::findReadyIds)).doesNotContain(futureId);
        transactionExecutor.execute(tenantOne, () -> dispatcher.dispatch(futureId));

        NotificationCommand noRecipients = new NotificationCommand(
            "no-recipients",
            NotificationSource.INVENTORY,
            "ITEM",
            "91",
            "ITEM_CREATED",
            "Inventario: oggetto creato",
            "È stato creato un oggetto.",
            NotificationSeverity.SUCCESS,
            "/inventory",
            "actor-1",
            "Mario Rossi",
            Set.of(NotificationAudience.role(com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum.ROLE_TREASURER)),
            null
        );
        long emptyId = enqueue(tenantOne, noRecipients);
        RuntimeException failure = org.assertj.core.api.Assertions.catchRuntimeException(() ->
            transactionExecutor.execute(tenantOne, () -> dispatcher.dispatch(emptyId))
        );
        assertThat(failure).isInstanceOf(IllegalStateException.class);
        transactionExecutor.execute(tenantOne, () -> dispatcher.markFailure(emptyId, failure));

        transactionExecutor.execute(tenantOne, () -> {
            assertThat(noticesRepository.findAll()).isEmpty();
            NotificationOutbox empty = outboxRepository.findById(emptyId).orElseThrow();
            assertThat(empty.getStatus()).isEqualTo(NotificationStatus.PENDING);
            assertThat(empty.getAttempts()).isEqualTo(1);
        });
    }

    @Test
    void deliveryIsStrictlySeparatedByTenant() {
        long tenantOneId = enqueue(tenantOne, command("shared-event", "recipient-1"));
        long tenantTwoId = enqueue(tenantTwo, command("shared-event", "recipient-2"));

        transactionExecutor.execute(tenantOne, () -> dispatcher.dispatch(tenantOneId));

        assertThat(transactionExecutor.execute(tenantOne, (java.util.function.Supplier<Long>) noticesRepository::count)).isEqualTo(1);
        assertThat(transactionExecutor.execute(tenantTwo, (java.util.function.Supplier<Long>) noticesRepository::count)).isZero();
        assertThat(transactionExecutor.execute(tenantTwo, () -> outboxRepository.findById(tenantTwoId).orElseThrow().getStatus()))
            .isEqualTo(NotificationStatus.PENDING);
    }

    @Test
    void cleanupDeletesOnlyOldDeliveredEventsAndNoticesKeepThe365DayRetention() {
        long oldDelivered = enqueue(tenantOne, command("old-delivered", "recipient-1"));
        long recentDelivered = enqueue(tenantOne, command("recent-delivered", "recipient-1"));
        long oldPending = enqueue(tenantOne, command("old-pending", "recipient-1"));
        transactionExecutor.execute(tenantOne, () -> {
            setDelivery(oldDelivered, NotificationStatus.DELIVERED, ZonedDateTime.now().minusDays(31));
            setDelivery(recentDelivered, NotificationStatus.DELIVERED, ZonedDateTime.now().minusDays(29));
            setDelivery(oldPending, NotificationStatus.PENDING, null);
            noticesRepository.save(notice("old-notice", ZonedDateTime.now().minusDays(366)));
            noticesRepository.save(notice("recent-notice", ZonedDateTime.now().minusDays(364)));
        });

        transactionExecutor.execute(tenantOne, () -> {
            assertThat(dispatcher.deleteDeliveredBefore(ZonedDateTime.now().minusDays(30))).isEqualTo(1);
            dataErasureService.purgeExpiredData();
        });

        transactionExecutor.execute(tenantOne, () -> {
            assertThat(outboxRepository.existsById(oldDelivered)).isFalse();
            assertThat(outboxRepository.existsById(recentDelivered)).isTrue();
            assertThat(outboxRepository.existsById(oldPending)).isTrue();
            assertThat(noticesRepository.findAll()).extracting(Notices::getUserId).containsExactly("recent-notice");
        });
    }

    @Test
    void rollingCompatibilityPreservesLegacyPendingEventsAndBackfillsCsvRoles() {
        transactionExecutor.execute(tenantOne, () -> entityManager.createNativeQuery("""
            INSERT INTO finance_notification_outbox(
                event_key, aggregate_type, aggregate_id, operation, title, message, severity,
                actor_id, actor_display_name, recipient_roles, occurred_at, status, attempts, next_attempt_at
            ) VALUES (?1, 'MOVEMENT', 88, 'MOVEMENT_CREATED', 'Economia: movimento registrato',
                      'Evento legacy', 'INFO', 'legacy-actor', 'Legacy Actor', ?2, CURRENT_TIMESTAMP,
                      'PENDING', 0, CURRENT_TIMESTAMP)
            """)
            .setParameter(1, "legacy-pending")
            .setParameter(2, "ROLE_ADMIN, ROLE_TREASURER")
            .executeUpdate());

        transactionExecutor.execute(tenantOne, () -> {
            NotificationOutbox event = outboxRepository.findAll().get(0);
            assertThat(event.getEventKey()).isEqualTo("legacy-pending");
            assertThat(event.getSource()).isEqualTo(NotificationSource.FINANCE);
            assertThat(event.getStatus()).isEqualTo(NotificationStatus.PENDING);
            assertThat(event.getAudiences()).extracting(audience -> audience.getValue())
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_TREASURER");
        });
    }

    private void dispatchAfter(CountDownLatch start, long eventId) {
        try {
            start.await();
            transactionExecutor.execute(tenantOne, () -> dispatcher.dispatch(eventId));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
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

    private void setDelivery(long id, NotificationStatus status, ZonedDateTime deliveredAt) {
        NotificationOutbox event = outboxRepository.findById(id).orElseThrow();
        event.setStatus(status);
        event.setDeliveredAt(deliveredAt);
        outboxRepository.save(event);
    }

    private static NotificationCommand command(String eventKey, String... recipients) {
        return new NotificationCommand(
            eventKey,
            NotificationSource.INVENTORY,
            "ITEM",
            "42",
            "ITEM_CREATED",
            "Inventario: oggetto creato",
            "Mario Rossi ha creato un oggetto.",
            NotificationSeverity.SUCCESS,
            "/inventory",
            "actor-1",
            "Mario Rossi",
            java.util.Arrays.stream(recipients).map(NotificationAudience::user).collect(java.util.stream.Collectors.toSet()),
            null
        );
    }

    private static NotificationDelivery delivery(String eventKey, String recipient) {
        return new NotificationDelivery(
            recipient,
            eventKey,
            "Inventario: oggetto creato",
            "Mario Rossi ha creato un oggetto.",
            NotificationSource.INVENTORY,
            NotificationSeverity.SUCCESS,
            "/inventory",
            "actor-1",
            com.fundaro.zodiac.taurus.domain.notification.NotificationPreferencePolicy.CONFIGURABLE
        );
    }

    private static Notices notice(String userId, ZonedDateTime insertDate) {
        Notices notice = new Notices();
        notice.setName("Avviso retention");
        notice.setMessage("Messaggio");
        notice.setUserId(userId);
        notice.setDeleted(false);
        notice.setInsertBy("test");
        notice.setInsertDate(insertDate);
        notice.setEditBy("test");
        notice.setEditDate(insertDate);
        return notice;
    }
}
