package com.fundaro.zodiac.taurus.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fundaro.zodiac.taurus.IntegrationTest;
import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSeverity;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSource;
import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaProvisioningService;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import com.fundaro.zodiac.taurus.repository.notification.NotificationOutboxRepository;
import com.fundaro.zodiac.taurus.service.notification.NotificationAudience;
import com.fundaro.zodiac.taurus.service.notification.NotificationCommand;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.IllegalTransactionStateException;

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
class NotificationOutboxPublisherIT {

    @MockBean ClientRegistrationRepository clientRegistrationRepository;
    @MockBean JwtDecoder jwtDecoder;
    @MockBean NotificationScheduler notificationScheduler;
    @Autowired TenantSchemaProvisioningService provisioningService;
    @Autowired TenantTransactionExecutor transactionExecutor;
    @Autowired NotificationOutboxPublisher publisher;
    @Autowired NotificationOutboxRepository repository;

    private final String tenantOne = "notification-it-a-" + UUID.randomUUID();
    private final String tenantTwo = "notification-it-b-" + UUID.randomUUID();

    @BeforeEach
    void provisionTenants() {
        provisioningService.provision(tenantOne);
        provisioningService.provision(tenantTwo);
    }

    @AfterEach
    void dropTenants() {
        provisioningService.dropSchema(tenantOne);
        provisioningService.dropSchema(tenantTwo);
    }

    @Test
    void requiresATransactionAndRollsBackWithTheDomainWork() {
        assertThatThrownBy(() -> publisher.enqueue(command("outside-transaction")))
            .isInstanceOf(IllegalTransactionStateException.class);

        assertThatThrownBy(() -> transactionExecutor.execute(tenantOne, () -> {
            publisher.enqueue(command("rolled-back"));
            throw new IllegalStateException("force rollback");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(transactionExecutor.execute(tenantOne, () -> repository.existsByEventKey("rolled-back"))).isFalse();
    }

    @Test
    void persistsAudienceAndAllowsTheSameEventKeyInDifferentTenants() {
        NotificationCommand command = command("same-event-key");

        transactionExecutor.execute(tenantOne, () -> publisher.enqueue(command));
        transactionExecutor.execute(tenantTwo, () -> publisher.enqueue(command));

        assertThat(transactionExecutor.execute(tenantOne, () -> { return repository.count(); })).isEqualTo(1);
        assertThat(transactionExecutor.execute(tenantTwo, () -> { return repository.count(); })).isEqualTo(1);
        assertThat(
            transactionExecutor.execute(
                tenantOne,
                () -> repository.findAll().get(0).getAudiences().stream().map(audience -> audience.getValue()).toList()
            )
        )
            .containsExactly("ROLE_ADMIN");
    }

    private static NotificationCommand command(String eventKey) {
        return new NotificationCommand(
            eventKey,
            NotificationSource.INVENTORY,
            "ITEM",
            "42",
            "ITEM_CREATED",
            "Inventario: oggetto creato",
            "Mario Rossi ha creato l'oggetto “INV-42 — Leggio”.",
            NotificationSeverity.SUCCESS,
            "/inventory",
            "actor-1",
            "Mario Rossi",
            Set.of(NotificationAudience.role(RoleEnum.ROLE_ADMIN)),
            null
        );
    }
}
