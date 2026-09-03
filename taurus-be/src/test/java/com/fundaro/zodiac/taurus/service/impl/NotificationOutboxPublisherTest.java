package com.fundaro.zodiac.taurus.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.domain.notification.NotificationOutbox;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSeverity;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSource;
import com.fundaro.zodiac.taurus.domain.notification.NotificationStatus;
import com.fundaro.zodiac.taurus.repository.notification.NotificationOutboxRepository;
import com.fundaro.zodiac.taurus.service.notification.NotificationAudience;
import com.fundaro.zodiac.taurus.service.notification.NotificationCommand;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationOutboxPublisherTest {

    @Mock NotificationOutboxRepository repository;

    @Test
    void persistsTheComposedCommandAndNormalizedAudience() {
        NotificationOutboxPublisher publisher = new NotificationOutboxPublisher(repository);
        when(repository.existsByEventKey("event-1")).thenReturn(false);

        publisher.enqueue(command("event-1", "/inventory"));

        ArgumentCaptor<NotificationOutbox> captor = ArgumentCaptor.forClass(NotificationOutbox.class);
        verify(repository).save(captor.capture());
        NotificationOutbox event = captor.getValue();
        assertThat(event.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(event.getSource()).isEqualTo(NotificationSource.INVENTORY);
        assertThat(event.getAudiences()).singleElement().satisfies(audience -> {
            assertThat(audience.getValue()).isEqualTo("ROLE_ADMIN");
            assertThat(audience.getEvent()).isSameAs(event);
        });
    }

    @Test
    void rejectsExternalTargetsMarkupAndInvalidAudienceValues() {
        NotificationOutboxPublisher publisher = new NotificationOutboxPublisher(repository);

        assertThatThrownBy(() -> publisher.enqueue(command("event-1", "https://example.test")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("internal");
        NotificationCommand markup = command("event-2", "/inventory");
        assertThatThrownBy(() -> publisher.enqueue(new NotificationCommand(
            markup.eventKey(), markup.source(), markup.aggregateType(), markup.aggregateId(), markup.operation(),
            "<b>Titolo</b>", markup.message(), markup.severity(), markup.targetPath(), markup.actorId(),
            markup.actorDisplayName(), markup.audiences(), null
        ))).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("plain text");
    }

    @Test
    void hashesOverlongEventKeysDeterministically() {
        NotificationOutboxPublisher publisher = new NotificationOutboxPublisher(repository);
        String longKey = "finance:movement:" + "component-".repeat(30);

        publisher.enqueue(command(longKey, "/finance"));

        ArgumentCaptor<NotificationOutbox> captor = ArgumentCaptor.forClass(NotificationOutbox.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getEventKey())
            .startsWith("sha256:")
            .hasSize(71)
            .isEqualTo(com.fundaro.zodiac.taurus.service.notification.NotificationEventKey.fit(longKey));
    }

    private static NotificationCommand command(String eventKey, String targetPath) {
        return new NotificationCommand(
            eventKey,
            NotificationSource.INVENTORY,
            "ITEM",
            "12",
            "ITEM_CREATED",
            "Inventario: oggetto creato",
            "Mario ha creato un oggetto.",
            NotificationSeverity.SUCCESS,
            targetPath,
            "actor-1",
            "Mario Rossi",
            Set.of(NotificationAudience.role(RoleEnum.ROLE_ADMIN)),
            null
        );
    }
}
