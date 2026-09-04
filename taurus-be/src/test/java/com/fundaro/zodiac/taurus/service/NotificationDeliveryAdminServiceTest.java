package com.fundaro.zodiac.taurus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fundaro.zodiac.taurus.domain.notification.NotificationOutbox;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSource;
import com.fundaro.zodiac.taurus.domain.notification.NotificationStatus;
import com.fundaro.zodiac.taurus.repository.notification.NotificationOutboxRepository;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class NotificationDeliveryAdminServiceTest {

    @Mock NotificationOutboxRepository repository;

    @Test
    void retriesTheSameFailedRowWithoutChangingItsEventKey() {
        NotificationOutbox event = failedEvent();
        when(repository.findByIdForUpdate(10L)).thenReturn(Optional.of(event));
        NotificationDeliveryAdminService service = new NotificationDeliveryAdminService(repository);

        var result = service.retry(10L, authentication());

        assertThat(event.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(event.getAttempts()).isZero();
        assertThat(event.getEventKey()).isEqualTo("inventory:return:10");
        assertThat(event.getLastError()).isNull();
        assertThat(result.eventKeyHash()).hasSize(16);
        verify(repository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsDuplicateBulkIdsBeforeLockingRows() {
        NotificationDeliveryAdminService service = new NotificationDeliveryAdminService(repository);

        assertThatThrownBy(() -> service.retry(List.of(10L, 10L), authentication()))
            .isInstanceOf(RequestAlertException.class)
            .hasMessageContaining("Duplicate");
    }

    private static NotificationOutbox failedEvent() {
        NotificationOutbox event = new NotificationOutbox();
        event.setId(10L);
        event.initializeAudit("dispatcher");
        event.setEventKey("inventory:return:10");
        event.setSource(NotificationSource.INVENTORY);
        event.setOperation("RETURN_REQUESTED");
        event.setOccurredAt(ZonedDateTime.now().minusHours(2));
        event.setStatus(NotificationStatus.FAILED);
        event.setAttempts(3);
        event.setNextAttemptAt(ZonedDateTime.now().minusHours(1));
        event.setLastError("java.lang.IllegalStateException: unavailable");
        return event;
    }

    private static JwtAuthenticationToken authentication() {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("admin-1")
            .issuedAt(now).expiresAt(now.plusSeconds(300)).build();
        return new JwtAuthenticationToken(jwt);
    }
}
