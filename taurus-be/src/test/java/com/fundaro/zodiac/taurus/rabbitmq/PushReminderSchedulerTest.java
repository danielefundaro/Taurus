package com.fundaro.zodiac.taurus.rabbitmq;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fundaro.zodiac.taurus.domain.PushReminder;
import com.fundaro.zodiac.taurus.domain.notification.NotificationPushMode;
import com.fundaro.zodiac.taurus.domain.notification.NotificationPushPreview;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSource;
import com.fundaro.zodiac.taurus.multitenancy.TenantContext;
import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaRegistry;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import com.fundaro.zodiac.taurus.repository.PushReminderRepository;
import com.fundaro.zodiac.taurus.service.NotificationPreferenceResolver;
import com.fundaro.zodiac.taurus.service.PushService;
import com.fundaro.zodiac.taurus.service.notification.NotificationPreferenceDecision;
import com.fundaro.zodiac.taurus.service.notification.PushDeliveryResult;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class PushReminderSchedulerTest {

    @Test
    void sendsCalendarReminderToTheApplicationEventRoute() {
        PushReminderRepository reminderRepository = mock(PushReminderRepository.class);
        PushService pushService = mock(PushService.class);
        TenantSchemaRegistry tenantSchemaRegistry = mock(TenantSchemaRegistry.class);
        TenantTransactionExecutor tenantTransactionExecutor = mock(TenantTransactionExecutor.class);
        NotificationPreferenceResolver preferenceResolver = mock(NotificationPreferenceResolver.class);
        PushReminder reminder = reminder();

        when(tenantSchemaRegistry.findActiveTenantCodes()).thenReturn(List.of("BMCDG"));
        doAnswer(invocation -> {
            String tenantCode = invocation.getArgument(0);
            Runnable action = invocation.getArgument(1);
            TenantContext.run(tenantCode, action);
            return null;
        }).when(tenantTransactionExecutor).execute(eq("BMCDG"), any(Runnable.class));
        when(reminderRepository.findByDeletedFalseAndSentFalseAndSendAtLessThanEqual(any(Instant.class)))
            .thenReturn(List.of(reminder));
        when(preferenceResolver.resolve(eq(NotificationSource.CALENDAR), any(), eq(java.util.Set.of("user-1"))))
            .thenReturn(Map.of("user-1", enabledPreferences()));
        when(pushService.sendToUserNow(eq("user-1"), eq("BMCDG"), anyString(), anyString(), eq("/calendar/42")))
            .thenReturn(new PushDeliveryResult(1, 0, 0, 0, 1));

        PushReminderScheduler scheduler = new PushReminderScheduler(
            reminderRepository,
            pushService,
            tenantSchemaRegistry,
            tenantTransactionExecutor
        );
        scheduler.setPreferenceResolver(preferenceResolver);

        scheduler.processReminders();

        verify(pushService).sendToUserNow("user-1", "BMCDG", "Promemoria evento", "L'evento \"Prova\" sta per iniziare", "/calendar/42");
        verify(reminderRepository).save(reminder);
    }

    private static PushReminder reminder() {
        PushReminder reminder = new PushReminder();
        reminder.setEventId(42L);
        reminder.setEventName("Prova");
        reminder.setUserId("user-1");
        reminder.setSendAt(Instant.now().minusSeconds(60));
        reminder.setEventStartAt(Instant.now().plusSeconds(3600));
        return reminder;
    }

    private static NotificationPreferenceDecision enabledPreferences() {
        return new NotificationPreferenceDecision(
            "user-1",
            true,
            NotificationPushMode.IMMEDIATE,
            true,
            ZoneId.of("Europe/Rome"),
            LocalTime.of(8, 0),
            false,
            LocalTime.of(22, 0),
            LocalTime.of(7, 0),
            null,
            NotificationPushPreview.FULL,
            false
        );
    }
}
