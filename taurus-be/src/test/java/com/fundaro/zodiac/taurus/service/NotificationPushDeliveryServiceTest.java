package com.fundaro.zodiac.taurus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.Notices;
import com.fundaro.zodiac.taurus.domain.Users;
import com.fundaro.zodiac.taurus.domain.notification.NotificationOutbox;
import com.fundaro.zodiac.taurus.domain.notification.NotificationPreferencePolicy;
import com.fundaro.zodiac.taurus.domain.notification.NotificationPushDelivery;
import com.fundaro.zodiac.taurus.domain.notification.NotificationPushDeliveryType;
import com.fundaro.zodiac.taurus.domain.notification.NotificationPushMode;
import com.fundaro.zodiac.taurus.domain.notification.NotificationPushPreview;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSource;
import com.fundaro.zodiac.taurus.domain.notification.NotificationStatus;
import com.fundaro.zodiac.taurus.multitenancy.TenantContext;
import com.fundaro.zodiac.taurus.repository.PushSubscriptionRepository;
import com.fundaro.zodiac.taurus.repository.UsersRepository;
import com.fundaro.zodiac.taurus.repository.notification.NotificationPushDeliveryRepository;
import com.fundaro.zodiac.taurus.service.notification.NotificationPreferenceDecision;
import com.fundaro.zodiac.taurus.service.notification.PushDeliveryResult;
import jakarta.persistence.EntityManager;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NotificationPushDeliveryServiceTest {

    private static final ZoneId ROME = ZoneId.of("Europe/Rome");

    private final NotificationPushDeliveryRepository repository = mock(NotificationPushDeliveryRepository.class);
    private final PushSubscriptionRepository subscriptionRepository = mock(PushSubscriptionRepository.class);
    private final UsersRepository usersRepository = mock(UsersRepository.class);
    private final NotificationPreferenceResolver preferenceResolver = mock(NotificationPreferenceResolver.class);
    private final PushService pushService = mock(PushService.class);
    private NotificationPushDeliveryService service;
    private TenantContext.Scope tenantScope;

    @BeforeEach
    void setUp() {
        tenantScope = TenantContext.use("tenant-a");
        service = new NotificationPushDeliveryService(
            repository,
            subscriptionRepository,
            mock(EntityManager.class),
            usersRepository,
            preferenceResolver,
            pushService,
            new ApplicationProperties()
        );
    }

    @AfterEach
    void tearDown() {
        tenantScope.close();
    }

    @Test
    void doesNotEnqueueAnythingWhenTheCategoryPushIsOff() {
        service.enqueue(outbox(), preference(NotificationPushMode.OFF, NotificationPushPreview.PRIVATE), 5L);

        verify(repository, never()).save(any());
    }

    @Test
    void enqueuesAnImmediateJobWithoutADigestBucket() {
        when(repository.existsBySourceEventKeyAndUserIdAndDeliveryTypeAndDeletedFalse(anyString(), anyString(), any())).thenReturn(false);

        service.enqueue(outbox(), preference(NotificationPushMode.IMMEDIATE, NotificationPushPreview.PRIVATE), null);

        NotificationPushDelivery saved = captureSaved();
        assertThat(saved.getDeliveryType()).isEqualTo(NotificationPushDeliveryType.IMMEDIATE);
        assertThat(saved.getDigestLocalDate()).isNull();
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(saved.getExpiresAt()).isAfter(saved.getScheduledAt());
    }

    @Test
    void enqueuesADigestItemStampedWithItsLocalBucketDate() {
        when(repository.existsBySourceEventKeyAndUserIdAndDeliveryTypeAndDeletedFalse(anyString(), anyString(), any())).thenReturn(false);

        service.enqueue(outbox(), preference(NotificationPushMode.DAILY_DIGEST, NotificationPushPreview.PRIVATE), null);

        NotificationPushDelivery saved = captureSaved();
        assertThat(saved.getDeliveryType()).isEqualTo(NotificationPushDeliveryType.DIGEST_ITEM);
        assertThat(saved.getDigestLocalDate()).isEqualTo(saved.getScheduledAt().withZoneSameInstant(ROME).toLocalDate());
    }

    @Test
    void skipsTheEnqueueWhenAnEquivalentJobAlreadyExists() {
        when(repository.existsBySourceEventKeyAndUserIdAndDeliveryTypeAndDeletedFalse(anyString(), anyString(), any())).thenReturn(true);

        service.enqueue(outbox(), preference(NotificationPushMode.IMMEDIATE, NotificationPushPreview.PRIVATE), null);

        verify(repository, never()).save(any());
    }

    @Test
    void doesNotEnqueueASnoozeJobWithoutAnActiveSubscription() {
        when(subscriptionRepository.existsByUserIdAndDeletedFalse("user-1")).thenReturn(false);

        service.enqueueSnooze(snoozedNotice(), preference(NotificationPushMode.OFF, NotificationPushPreview.PRIVATE));

        verify(repository, never()).save(any());
    }

    @Test
    void enqueuesASnoozeJobRegardlessOfTheCategoryPushMode() {
        when(subscriptionRepository.existsByUserIdAndDeletedFalse("user-1")).thenReturn(true);
        when(repository.existsByNoticeIdAndSnoozeRevisionAndDeliveryTypeAndDeletedFalse(any(), any(), any())).thenReturn(false);

        service.enqueueSnooze(snoozedNotice(), preference(NotificationPushMode.OFF, NotificationPushPreview.PRIVATE));

        NotificationPushDelivery saved = captureSaved();
        assertThat(saved.getDeliveryType()).isEqualTo(NotificationPushDeliveryType.SNOOZE);
        assertThat(saved.getSnoozeRevision()).isEqualTo(3);
    }

    @Test
    void sendsAGenericPayloadWhenThePreviewIsPrivate() {
        NotificationPushDelivery delivery = pendingImmediate();
        primeProcessing(delivery, NotificationPushMode.IMMEDIATE, NotificationPushPreview.PRIVATE);
        when(pushService.sendToUserNow(anyString(), anyString(), anyString(), anyString(), any()))
            .thenReturn(new PushDeliveryResult(1, 0, 0, 0, 1));

        service.process(1L);

        verify(pushService).sendToUserNow(eq("user-1"), eq("tenant-a"), eq("Taurus"), eq("Hai un nuovo aggiornamento"), eq("/inventory"));
        assertThat(delivery.getStatus()).isEqualTo(NotificationStatus.DELIVERED);
    }

    @Test
    void sendsTheEditorialPayloadWhenThePreviewIsFull() {
        NotificationPushDelivery delivery = pendingImmediate();
        primeProcessing(delivery, NotificationPushMode.IMMEDIATE, NotificationPushPreview.FULL);
        when(pushService.sendToUserNow(anyString(), anyString(), anyString(), anyString(), any()))
            .thenReturn(new PushDeliveryResult(1, 0, 0, 0, 1));

        service.process(1L);

        verify(pushService).sendToUserNow(eq("user-1"), eq("tenant-a"), eq("Titolo"), eq("Messaggio"), eq("/inventory"));
    }

    @Test
    void skipsAJobWhoseCategoryPreferenceChangedAfterTheFanout() {
        NotificationPushDelivery delivery = pendingImmediate();
        primeProcessing(delivery, NotificationPushMode.OFF, NotificationPushPreview.PRIVATE);

        service.process(1L);

        assertThat(delivery.getStatus()).isEqualTo(NotificationStatus.SKIPPED);
        assertThat(delivery.getSkipReason()).isEqualTo("PREFERENCE_CHANGED");
        verify(pushService, never()).sendToUserNow(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void skipsAJobWhoseNoticeWasReadBeforeTheAttempt() {
        NotificationPushDelivery delivery = pendingImmediate();
        Notices notice = new Notices();
        notice.setId(9L);
        notice.setReadDate(ZonedDateTime.now());
        delivery.setNotice(notice);
        primeProcessing(delivery, NotificationPushMode.IMMEDIATE, NotificationPushPreview.PRIVATE);

        service.process(1L);

        assertThat(delivery.getStatus()).isEqualTo(NotificationStatus.SKIPPED);
        assertThat(delivery.getSkipReason()).isEqualTo("NOTICE_CHANGED");
    }

    @Test
    void skipsAnExpiredJobInsteadOfSendingItLate() {
        NotificationPushDelivery delivery = pendingImmediate();
        delivery.setExpiresAt(ZonedDateTime.now().minusMinutes(1));
        primeProcessing(delivery, NotificationPushMode.IMMEDIATE, NotificationPushPreview.PRIVATE);

        service.process(1L);

        assertThat(delivery.getStatus()).isEqualTo(NotificationStatus.SKIPPED);
        assertThat(delivery.getSkipReason()).isEqualTo("EXPIRED");
    }

    @Test
    void closesAJobWithoutAnyDeviceAsSkippedAndNotAsAFailure() {
        NotificationPushDelivery delivery = pendingImmediate();
        primeProcessing(delivery, NotificationPushMode.IMMEDIATE, NotificationPushPreview.PRIVATE);
        when(pushService.sendToUserNow(anyString(), anyString(), anyString(), anyString(), any()))
            .thenReturn(PushDeliveryResult.noDevices());

        service.process(1L);

        assertThat(delivery.getStatus()).isEqualTo(NotificationStatus.SKIPPED);
        assertThat(delivery.getSkipReason()).isEqualTo("NO_SUBSCRIPTION");
        assertThat(delivery.getNextAttemptAt()).isNull();
    }

    @Test
    void reschedulesATemporaryProviderFailureAndGivesUpAfterTheAttemptLimit() {
        NotificationPushDelivery delivery = pendingImmediate();
        primeProcessing(delivery, NotificationPushMode.IMMEDIATE, NotificationPushPreview.PRIVATE);
        when(pushService.sendToUserNow(anyString(), anyString(), anyString(), anyString(), any()))
            .thenReturn(new PushDeliveryResult(0, 0, 1, 0, 1));

        service.process(1L);

        assertThat(delivery.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(delivery.getAttempts()).isEqualTo(1);
        assertThat(delivery.getNextAttemptAt()).isAfter(ZonedDateTime.now());
        assertThat(delivery.getLastError()).isEqualTo("TEMPORARY_PROVIDER_FAILURE");

        delivery.setAttempts(7);
        delivery.setStatus(NotificationStatus.PENDING);
        delivery.setNextAttemptAt(ZonedDateTime.now().minusMinutes(1));
        service.process(1L);

        assertThat(delivery.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(delivery.getAttempts()).isEqualTo(8);
    }

    @Test
    void failsImmediatelyOnAPermanentProviderError() {
        NotificationPushDelivery delivery = pendingImmediate();
        primeProcessing(delivery, NotificationPushMode.IMMEDIATE, NotificationPushPreview.PRIVATE);
        when(pushService.sendToUserNow(anyString(), anyString(), anyString(), anyString(), any()))
            .thenReturn(new PushDeliveryResult(0, 0, 0, 1, 1));

        service.process(1L);

        assertThat(delivery.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(delivery.getLastError()).isEqualTo("PERMANENT_PROVIDER_FAILURE");
        assertThat(delivery.getNextAttemptAt()).isNull();
    }

    @Test
    void sendsOneAggregatedDigestWithoutNamesOrAmounts() {
        NotificationPushDelivery calendarOne = digestItem(1L, NotificationSource.CALENDAR);
        NotificationPushDelivery calendarTwo = digestItem(2L, NotificationSource.CALENDAR);
        NotificationPushDelivery finance = digestItem(3L, NotificationSource.FINANCE);
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(calendarOne));
        when(repository.findDigestForUpdate(anyString(), any(), any(), any(), any()))
            .thenReturn(List.of(calendarOne, calendarTwo, finance));
        activeUser();
        when(preferenceResolver.resolve(any(), eq(NotificationPreferencePolicy.CONFIGURABLE), any()))
            .thenReturn(Map.of("user-1", preference(NotificationPushMode.DAILY_DIGEST, NotificationPushPreview.FULL)));
        when(pushService.sendToUserNow(anyString(), anyString(), anyString(), anyString(), any()))
            .thenReturn(new PushDeliveryResult(1, 0, 0, 0, 1));

        service.process(1L);

        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(pushService).sendToUserNow(eq("user-1"), eq("tenant-a"), title.capture(), body.capture(), eq("/dashboard?section=notifications"));
        assertThat(title.getValue()).isEqualTo("Taurus: riepilogo giornaliero");
        assertThat(body.getValue()).startsWith("Hai 3 aggiornamenti:").contains("calendario").contains("economia");
        // Nemmeno con anteprima FULL il digest espone il testo editoriale delle singole notifiche.
        assertThat(body.getValue()).doesNotContain("Titolo").doesNotContain("Messaggio");
        assertThat(calendarOne.getStatus()).isEqualTo(NotificationStatus.DELIVERED);
        assertThat(finance.getStatus()).isEqualTo(NotificationStatus.DELIVERED);
    }

    @Test
    void excludesReadItemsFromTheDigestAndKeepsTheRest() {
        NotificationPushDelivery kept = digestItem(1L, NotificationSource.CALENDAR);
        NotificationPushDelivery read = digestItem(2L, NotificationSource.INVENTORY);
        Notices notice = new Notices();
        notice.setId(11L);
        notice.setReadDate(ZonedDateTime.now());
        read.setNotice(notice);
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(kept));
        when(repository.findDigestForUpdate(anyString(), any(), any(), any(), any())).thenReturn(List.of(kept, read));
        activeUser();
        when(preferenceResolver.resolve(any(), eq(NotificationPreferencePolicy.CONFIGURABLE), any()))
            .thenReturn(Map.of("user-1", preference(NotificationPushMode.DAILY_DIGEST, NotificationPushPreview.PRIVATE)));
        when(pushService.sendToUserNow(anyString(), anyString(), anyString(), anyString(), any()))
            .thenReturn(new PushDeliveryResult(1, 0, 0, 0, 1));

        service.process(1L);

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(pushService).sendToUserNow(anyString(), anyString(), anyString(), body.capture(), any());
        assertThat(body.getValue()).startsWith("Hai 1 aggiornamenti:");
        assertThat(read.getStatus()).isEqualTo(NotificationStatus.SKIPPED);
        assertThat(read.getSkipReason()).isEqualTo("NOT_ELIGIBLE");
    }

    @Test
    void skipsEveryJobOfAnInactiveUser() {
        NotificationPushDelivery delivery = pendingImmediate();
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(delivery));
        Users user = new Users();
        user.setActive(false);
        when(usersRepository.findByKeycloakIdAndDeletedFalse("user-1")).thenReturn(Optional.of(user));

        service.process(1L);

        assertThat(delivery.getStatus()).isEqualTo(NotificationStatus.SKIPPED);
        assertThat(delivery.getSkipReason()).isEqualTo("USER_INACTIVE");
    }

    private void primeProcessing(NotificationPushDelivery delivery, NotificationPushMode mode, NotificationPushPreview preview) {
        when(repository.findByIdForUpdate(1L)).thenReturn(Optional.of(delivery));
        activeUser();
        when(preferenceResolver.resolve(any(), eq(NotificationPreferencePolicy.CONFIGURABLE), any()))
            .thenReturn(Map.of("user-1", preference(mode, preview)));
    }

    private void activeUser() {
        Users user = new Users();
        user.setActive(true);
        when(usersRepository.findByKeycloakIdAndDeletedFalse("user-1")).thenReturn(Optional.of(user));
    }

    private NotificationPushDelivery captureSaved() {
        ArgumentCaptor<NotificationPushDelivery> captor = ArgumentCaptor.forClass(NotificationPushDelivery.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }

    private static NotificationPushDelivery pendingImmediate() {
        NotificationPushDelivery delivery = new NotificationPushDelivery();
        delivery.setId(1L);
        delivery.initializeAudit("dispatcher");
        delivery.setSourceEventKey("inventory:create:1");
        delivery.setUserId("user-1");
        delivery.setSource(NotificationSource.INVENTORY);
        delivery.setDeliveryType(NotificationPushDeliveryType.IMMEDIATE);
        delivery.setTitle("Titolo");
        delivery.setMessage("Messaggio");
        delivery.setTargetPath("/inventory");
        delivery.setScheduledAt(ZonedDateTime.now().minusMinutes(5));
        delivery.setExpiresAt(ZonedDateTime.now().plusHours(12));
        delivery.setStatus(NotificationStatus.PENDING);
        delivery.setNextAttemptAt(ZonedDateTime.now().minusMinutes(1));
        return delivery;
    }

    private static NotificationPushDelivery digestItem(long id, NotificationSource source) {
        NotificationPushDelivery delivery = pendingImmediate();
        delivery.setId(id);
        delivery.setSource(source);
        delivery.setDeliveryType(NotificationPushDeliveryType.DIGEST_ITEM);
        delivery.setDigestLocalDate(ZonedDateTime.now(ROME).toLocalDate());
        return delivery;
    }

    private static Notices snoozedNotice() {
        Notices notice = new Notices();
        notice.setId(9L);
        notice.setName("Titolo");
        notice.setMessage("Messaggio");
        notice.setUserId("user-1");
        notice.setSource(NotificationSource.INVENTORY.name());
        notice.setTargetPath("/inventory");
        notice.setSnoozedUntil(ZonedDateTime.now().plusHours(1));
        notice.setSnoozeRevision(3);
        return notice;
    }

    private static NotificationOutbox outbox() {
        NotificationOutbox event = new NotificationOutbox();
        event.setId(1L);
        event.setEventKey("inventory:create:1");
        event.setSource(NotificationSource.INVENTORY);
        event.setOperation("CREATE");
        event.setTitle("Titolo");
        event.setMessage("Messaggio");
        event.setTargetPath("/inventory");
        event.setOccurredAt(ZonedDateTime.now());
        return event;
    }

    private static NotificationPreferenceDecision preference(NotificationPushMode mode, NotificationPushPreview preview) {
        return new NotificationPreferenceDecision(
            "user-1", true, mode, true, ROME, LocalTime.of(8, 0), false,
            LocalTime.of(22, 0), LocalTime.of(7, 0), null, preview, false
        );
    }
}
