package com.fundaro.zodiac.taurus.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.notification.NotificationStatus;
import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaProvisioningService;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import com.fundaro.zodiac.taurus.repository.CalendarEventsRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryAssignmentRepository;
import com.fundaro.zodiac.taurus.repository.inventory.InventoryErasureRequestRepository;
import com.fundaro.zodiac.taurus.repository.LastResearchRepository;
import com.fundaro.zodiac.taurus.repository.NoticesRepository;
import com.fundaro.zodiac.taurus.repository.PreferencesRepository;
import com.fundaro.zodiac.taurus.repository.PushReminderRepository;
import com.fundaro.zodiac.taurus.repository.PushSubscriptionRepository;
import com.fundaro.zodiac.taurus.repository.QueueUploadFilesRepository;
import com.fundaro.zodiac.taurus.repository.UserLegalAcceptanceRepository;
import com.fundaro.zodiac.taurus.repository.UsersRepository;
import com.fundaro.zodiac.taurus.repository.notification.NotificationProfileRepository;
import com.fundaro.zodiac.taurus.repository.notification.NotificationPushDeliveryRepository;
import com.fundaro.zodiac.taurus.service.MediaService;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * La finestra di conservazione dei job push viene dalla configurazione: ogni stato ha la sua, e
 * cambiarla nel file di proprietà deve cambiare il taglio senza toccare il codice.
 */
class DataErasureRetentionTest {

    private final NoticesRepository noticesRepository = mock(NoticesRepository.class);
    private final LastResearchRepository lastResearchRepository = mock(LastResearchRepository.class);
    private final PushReminderRepository pushReminderRepository = mock(PushReminderRepository.class);
    private final NotificationPushDeliveryRepository pushDeliveryRepository = mock(NotificationPushDeliveryRepository.class);

    @Test
    void readsEachRetentionWindowOfThePushJobsFromTheConfiguration() {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getNotificationPushDelivery().setDeliveredRetentionDays(7);
        properties.getNotificationPushDelivery().setSkippedRetentionDays(14);
        properties.getNotificationPushDelivery().setFailedRetentionDays(120);
        ZonedDateTime before = ZonedDateTime.now();

        service(properties).purgeExpiredData();

        ArgumentCaptor<ZonedDateTime> delivered = ArgumentCaptor.forClass(ZonedDateTime.class);
        verify(pushDeliveryRepository).deleteAllByStatusAndDeliveredAtBefore(eq(NotificationStatus.DELIVERED), delivered.capture());
        assertThat(delivered.getValue()).isBetween(before.minusDays(7).minusMinutes(1), ZonedDateTime.now().minusDays(7));

        ArgumentCaptor<ZonedDateTime> others = ArgumentCaptor.forClass(ZonedDateTime.class);
        verify(pushDeliveryRepository).deleteAllByStatusAndEditDateBefore(eq(NotificationStatus.SKIPPED), others.capture());
        assertThat(others.getValue()).isBeforeOrEqualTo(ZonedDateTime.now().minusDays(14));

        verify(pushDeliveryRepository).deleteAllByStatusAndEditDateBefore(eq(NotificationStatus.FAILED), others.capture());
        assertThat(others.getValue()).isBeforeOrEqualTo(ZonedDateTime.now().minusDays(120));
    }

    /** I promemoria pendenti non si cancellano mai per età: solo quelli già chiusi. */
    @Test
    void purgesOnlyTheClosedRemindersAndKeepsThePendingOnes() {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getRetention().setSentPushRemindersDays(3);

        service(properties).purgeExpiredData();

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(pushReminderRepository).deleteAllByStatusNotAndSendAtBefore(eq(NotificationStatus.PENDING), cutoff.capture());
        assertThat(cutoff.getValue()).isBefore(Instant.now().minus(2, ChronoUnit.DAYS));
        assertThat(cutoff.getValue()).isAfter(Instant.now().minus(4, ChronoUnit.DAYS));
    }

    private DataErasureService service(ApplicationProperties properties) {
        DataErasureService service = new DataErasureService(
            noticesRepository,
            lastResearchRepository,
            mock(PreferencesRepository.class),
            mock(PushSubscriptionRepository.class),
            pushReminderRepository,
            mock(UserLegalAcceptanceRepository.class),
            mock(UsersRepository.class),
            mock(QueueUploadFilesRepository.class),
            mock(CalendarEventsRepository.class),
            mock(MediaService.class),
            mock(TenantStorageService.class),
            properties,
            mock(InventoryAssignmentRepository.class),
            mock(InventoryErasureRequestRepository.class),
            mock(TenantSchemaProvisioningService.class),
            mock(TenantTransactionExecutor.class)
        );
        service.setNotificationErasureRepositories(mock(NotificationProfileRepository.class), pushDeliveryRepository);
        return service;
    }

    @Test
    void keepsTheNoticesAndTheSearchesOnTheirOwnWindows() {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getRetention().setNoticesDays(365);
        properties.getRetention().setLastResearchDays(90);

        service(properties).purgeExpiredData();

        verify(noticesRepository).deleteAllByInsertDateBefore(any(ZonedDateTime.class));
        verify(lastResearchRepository).deleteAllByInsertDateBefore(any(ZonedDateTime.class));
    }
}
