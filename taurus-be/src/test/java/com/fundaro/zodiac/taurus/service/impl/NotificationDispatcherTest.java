package com.fundaro.zodiac.taurus.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.notification.NotificationAudienceType;
import com.fundaro.zodiac.taurus.domain.notification.NotificationOutbox;
import com.fundaro.zodiac.taurus.domain.notification.NotificationOutboxAudience;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSeverity;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSource;
import com.fundaro.zodiac.taurus.domain.notification.NotificationStatus;
import com.fundaro.zodiac.taurus.repository.notification.NotificationOutboxRepository;
import com.fundaro.zodiac.taurus.service.NoticesService;
import com.fundaro.zodiac.taurus.service.notification.NotificationDelivery;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationDispatcherTest {

    @Mock NotificationOutboxRepository repository;
    @Mock NotificationRecipientResolver recipientResolver;
    @Mock NoticesService noticesService;
    @Mock NotificationMetrics metrics;
    private NotificationDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        dispatcher = new NotificationDispatcher(repository, recipientResolver, noticesService, new ApplicationProperties(), metrics);
    }

    @Test
    void deliversOnceToEveryDistinctRecipientAndMarksEventDelivered() {
        NotificationOutbox event = pendingEvent();
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(event));
        when(recipientResolver.resolve(event.getAudiences())).thenReturn(Set.of("admin-1", "treasurer-1"));

        dispatcher.dispatch(1L);

        ArgumentCaptor<NotificationDelivery> delivery = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(noticesService, org.mockito.Mockito.times(2)).addNoticeToUser(delivery.capture());
        assertThat(delivery.getAllValues()).extracting(NotificationDelivery::userId).containsExactlyInAnyOrder("admin-1", "treasurer-1");
        assertThat(delivery.getAllValues()).allSatisfy(value -> {
            assertThat(value.eventKey()).isEqualTo("event-1");
            assertThat(value.source()).isEqualTo(NotificationSource.FINANCE);
        });
        assertThat(event.getStatus()).isEqualTo(NotificationStatus.DELIVERED);
        assertThat(event.getDeliveredAt()).isNotNull();
        verify(repository).save(event);
    }

    @Test
    void schedulesAnExponentialRetryAndSanitizesTheError() {
        NotificationOutbox event = pendingEvent();
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(event));
        ZonedDateTime before = ZonedDateTime.now();

        dispatcher.markFailure(1L, new IllegalStateException("Keycloak temporaneamente non disponibile\nat com.example"));

        assertThat(event.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getNextAttemptAt()).isAfter(before.plusSeconds(50));
        assertThat(event.getLastError()).contains("IllegalStateException").doesNotContain("\n");
        verify(repository).save(event);
    }

    @Test
    void marksTheEventFailedWhenTheConfiguredAttemptLimitIsReached() {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getNotifications().getRetry().setMaxAttempts(1);
        dispatcher = new NotificationDispatcher(repository, recipientResolver, noticesService, properties, metrics);
        NotificationOutbox event = pendingEvent();
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(event));

        dispatcher.markFailure(1L, new IllegalStateException("unavailable"));

        assertThat(event.getStatus()).isEqualTo(NotificationStatus.FAILED);
    }

    private static NotificationOutbox pendingEvent() {
        NotificationOutbox event = new NotificationOutbox();
        event.setId(1L);
        event.initializeAudit("actor-1");
        event.setEventKey("event-1");
        event.setSource(NotificationSource.FINANCE);
        event.setAggregateType("MOVEMENT");
        event.setOperation("MOVEMENT_CREATED");
        event.setTitle("Titolo");
        event.setMessage("Messaggio");
        event.setSeverity(NotificationSeverity.INFO);
        event.setTargetPath("/finance?tab=movements");
        event.setActorId("actor-1");
        event.setActorDisplayName("Mario Rossi");
        event.setOccurredAt(ZonedDateTime.now().minusMinutes(1));
        event.setStatus(NotificationStatus.PENDING);
        event.setNextAttemptAt(ZonedDateTime.now().minusSeconds(1));
        NotificationOutboxAudience audience = new NotificationOutboxAudience();
        audience.setType(NotificationAudienceType.ROLE);
        audience.setValue("ROLE_ADMIN");
        event.addAudience(audience);
        return event;
    }
}
