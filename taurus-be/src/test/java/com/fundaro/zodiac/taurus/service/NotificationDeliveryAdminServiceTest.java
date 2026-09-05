package com.fundaro.zodiac.taurus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.PushReminder;
import com.fundaro.zodiac.taurus.domain.notification.NotificationDeliveryOrigin;
import com.fundaro.zodiac.taurus.domain.notification.NotificationOutbox;
import com.fundaro.zodiac.taurus.domain.notification.NotificationPushDelivery;
import com.fundaro.zodiac.taurus.domain.notification.NotificationPushDeliveryType;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSource;
import com.fundaro.zodiac.taurus.domain.notification.NotificationStatus;
import com.fundaro.zodiac.taurus.repository.PushReminderRepository;
import com.fundaro.zodiac.taurus.repository.notification.NotificationDeliveryAdminQueryRepository;
import com.fundaro.zodiac.taurus.repository.notification.NotificationDeliveryAdminQueryRepository.NotificationDeliveryRow;
import com.fundaro.zodiac.taurus.repository.notification.NotificationOutboxRepository;
import com.fundaro.zodiac.taurus.repository.notification.NotificationPushDeliveryRepository;
import com.fundaro.zodiac.taurus.service.dto.notification.NotificationDeliveryRef;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationDeliveryAdminServiceTest {

    @Mock NotificationDeliveryAdminQueryRepository queryRepository;
    @Mock NotificationOutboxRepository outboxRepository;
    @Mock NotificationPushDeliveryRepository pushDeliveryRepository;
    @Mock PushReminderRepository reminderRepository;

    private NotificationDeliveryAdminService service() {
        return new NotificationDeliveryAdminService(
            queryRepository,
            outboxRepository,
            pushDeliveryRepository,
            reminderRepository,
            new ApplicationProperties()
        );
    }

    @Test
    void retriesTheSameFailedOutboxRowWithoutChangingItsEventKey() {
        NotificationOutbox event = failedEvent();
        when(outboxRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(event));

        var result = service().retry(NotificationDeliveryOrigin.OUTBOX, 10L, authentication());

        assertThat(event.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(event.getAttempts()).isZero();
        assertThat(event.getEventKey()).isEqualTo("inventory:return:10");
        assertThat(event.getLastError()).isNull();
        assertThat(result.origin()).isEqualTo(NotificationDeliveryOrigin.OUTBOX);
        assertThat(result.rowKey()).isEqualTo("OUTBOX:10");
        assertThat(result.eventKeyHash()).hasSize(16);
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void reopensTheDeliveryWindowWhenRetryingAnExpiredPushJob() {
        NotificationPushDelivery delivery = failedPush();
        delivery.setExpiresAt(ZonedDateTime.now().minusDays(2));
        when(pushDeliveryRepository.findByIdForAdminUpdate(7L)).thenReturn(Optional.of(delivery));

        var result = service().retry(NotificationDeliveryOrigin.PUSH, 7L, authentication());

        assertThat(delivery.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(delivery.getAttempts()).isZero();
        assertThat(delivery.getSkipReason()).isNull();
        assertThat(delivery.getExpiresAt()).isAfter(ZonedDateTime.now());
        assertThat(result.deliveryType()).isEqualTo("IMMEDIATE");
    }

    @Test
    void reopensAReminderWithoutForcingItPastTheEventStart() {
        PushReminder reminder = failedReminder();
        when(reminderRepository.findByIdForUpdate(3L)).thenReturn(Optional.of(reminder));

        var result = service().retry(NotificationDeliveryOrigin.REMINDER, 3L, authentication());

        assertThat(reminder.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(reminder.isSent()).isFalse();
        // L'istante di partenza torna a "adesso": è lo scheduler a chiudere il job se l'evento è iniziato.
        assertThat(reminder.getEventStartAt()).isEqualTo(Instant.parse("2026-09-05T09:00:00Z"));
        assertThat(result.source()).isEqualTo(NotificationSource.CALENDAR);
        assertThat(result.deliveryType()).isEqualTo("EVENT_REMINDER");
    }

    @Test
    void refusesToRetryARowThatIsNotFailed() {
        NotificationPushDelivery delivery = failedPush();
        delivery.setStatus(NotificationStatus.SKIPPED);
        when(pushDeliveryRepository.findByIdForAdminUpdate(7L)).thenReturn(Optional.of(delivery));

        assertThatThrownBy(() -> service().retry(NotificationDeliveryOrigin.PUSH, 7L, authentication()))
            .isInstanceOf(RequestAlertException.class)
            .hasMessageContaining("Only failed");
    }

    @Test
    void refusesATechnicalCloseOnTheInAppFanout() {
        assertThatThrownBy(() -> service().close(NotificationDeliveryOrigin.OUTBOX, 10L, "MANUAL_CLOSE", authentication()))
            .isInstanceOf(RequestAlertException.class)
            .hasMessageContaining("cannot be closed");
    }

    @Test
    void closesAPushJobWithItsTechnicalReason() {
        NotificationPushDelivery delivery = failedPush();
        when(pushDeliveryRepository.findByIdForAdminUpdate(7L)).thenReturn(Optional.of(delivery));

        var result = service().close(NotificationDeliveryOrigin.PUSH, 7L, "DEVICE_UNREACHABLE", authentication());

        assertThat(delivery.getStatus()).isEqualTo(NotificationStatus.SKIPPED);
        assertThat(delivery.getSkipReason()).isEqualTo("DEVICE_UNREACHABLE");
        assertThat(delivery.getNextAttemptAt()).isNull();
        assertThat(result.skipReason()).isEqualTo("DEVICE_UNREACHABLE");
    }

    @Test
    void rejectsDuplicateBulkReferencesBeforeLockingRows() {
        NotificationDeliveryRef ref = new NotificationDeliveryRef(NotificationDeliveryOrigin.PUSH, 10L);

        assertThatThrownBy(() -> service().retry(List.of(ref, ref), authentication()))
            .isInstanceOf(RequestAlertException.class)
            .hasMessageContaining("Duplicate");
        verify(pushDeliveryRepository, never()).findByIdForAdminUpdate(any());
    }

    @Test
    void countsOnlyTheRowsThatWereActuallyFailedInABulkRetry() {
        NotificationOutbox event = failedEvent();
        NotificationPushDelivery alreadyDelivered = failedPush();
        alreadyDelivered.setStatus(NotificationStatus.DELIVERED);
        when(outboxRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(event));
        when(pushDeliveryRepository.findByIdForAdminUpdate(7L)).thenReturn(Optional.of(alreadyDelivered));

        var result = service().retry(
            List.of(
                new NotificationDeliveryRef(NotificationDeliveryOrigin.OUTBOX, 10L),
                new NotificationDeliveryRef(NotificationDeliveryOrigin.PUSH, 7L)
            ),
            authentication()
        );

        assertThat(result.retriedCount()).isEqualTo(1);
        assertThat(alreadyDelivered.getStatus()).isEqualTo(NotificationStatus.DELIVERED);
    }

    @Test
    void hashesTheEventKeyAndNeverExposesItInTheListing() {
        NotificationDeliveryRow row = new NotificationDeliveryRow(
            NotificationDeliveryOrigin.PUSH,
            7L,
            NotificationSource.CALENDAR,
            null,
            "IMMEDIATE",
            NotificationStatus.FAILED,
            ZonedDateTime.now().minusHours(1),
            2,
            ZonedDateTime.now(),
            null,
            "java.lang.IllegalStateException: unavailable",
            null,
            "calendar:event:42"
        );
        when(queryRepository.count(any())).thenReturn(1L);
        when(queryRepository.find(any(), org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt(), any(), org.mockito.ArgumentMatchers.anyBoolean()))
            .thenReturn(List.of(row));

        var page = service().find(NotificationStatus.FAILED, null, null, null, null, null, 0, 20, "occurredAt,asc");

        assertThat(page.getTotalElements()).isEqualTo(1);
        assertThat(page.getContent().get(0).eventKeyHash()).hasSize(16).isNotEqualTo("calendar:event:42");
        assertThat(page.getContent().get(0).errorClass()).isEqualTo("IllegalStateException");
        assertThat(page.getContent().get(0).rowKey()).isEqualTo("PUSH:7");
    }

    @Test
    void rejectsAnUnsupportedSortFieldAndAnInvertedRange() {
        assertThatThrownBy(() -> service().find(NotificationStatus.FAILED, null, null, null, null, null, 0, 20, "title,asc"))
            .isInstanceOf(RequestAlertException.class)
            .hasMessageContaining("sort field");

        ZonedDateTime now = ZonedDateTime.now();
        assertThatThrownBy(() -> service().find(NotificationStatus.FAILED, null, null, null, now, now.minusDays(1), 0, 20, "occurredAt,asc"))
            .isInstanceOf(RequestAlertException.class)
            .hasMessageContaining("must precede");
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

    private static NotificationPushDelivery failedPush() {
        NotificationPushDelivery delivery = new NotificationPushDelivery();
        delivery.setId(7L);
        delivery.initializeAudit("dispatcher");
        delivery.setSourceEventKey("calendar:event:42");
        delivery.setUserId("user-1");
        delivery.setSource(NotificationSource.CALENDAR);
        delivery.setDeliveryType(NotificationPushDeliveryType.IMMEDIATE);
        delivery.setTitle("Titolo");
        delivery.setMessage("Messaggio");
        delivery.setScheduledAt(ZonedDateTime.now().minusHours(3));
        delivery.setExpiresAt(ZonedDateTime.now().plusHours(3));
        delivery.setStatus(NotificationStatus.FAILED);
        delivery.setAttempts(8);
        delivery.setLastError("PERMANENT_PROVIDER_FAILURE");
        return delivery;
    }

    private static PushReminder failedReminder() {
        PushReminder reminder = new PushReminder();
        reminder.setId(3L);
        reminder.initializeAudit("scheduler");
        reminder.setEventId(42L);
        reminder.setEventName("Prova");
        reminder.setUserId("user-1");
        reminder.setSendAt(Instant.parse("2026-09-05T08:30:00Z"));
        reminder.setEventStartAt(Instant.parse("2026-09-05T09:00:00Z"));
        reminder.setStatus(NotificationStatus.FAILED);
        reminder.setSent(true);
        reminder.setAttempts(8);
        reminder.setLastError("PERMANENT_PROVIDER_FAILURE");
        return reminder;
    }

    private static JwtAuthenticationToken authentication() {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("admin-1")
            .issuedAt(now).expiresAt(now.plusSeconds(300)).build();
        return new JwtAuthenticationToken(jwt);
    }
}
