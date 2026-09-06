package com.fundaro.zodiac.taurus.service.eventpreparation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaRegistry;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import com.fundaro.zodiac.taurus.service.dto.eventpreparation.EventPreparationDtos.ClosureStatus;
import com.fundaro.zodiac.taurus.service.dto.eventpreparation.EventPreparationDtos.PreparationStatus;
import com.fundaro.zodiac.taurus.service.eventpreparation.EventPreparationService.DashboardEntry;
import com.fundaro.zodiac.taurus.service.impl.NotificationOutboxPublisher;
import com.fundaro.zodiac.taurus.service.notification.NotificationCommand;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EventPreparationNotificationSchedulerTest {

    @Test
    void emitsOneDeterministicNotificationForEachExpiredPreparationArea() {
        EventPreparationService service = mock(EventPreparationService.class);
        NotificationOutboxPublisher publisher = mock(NotificationOutboxPublisher.class);
        EventPreparationNotificationScheduler scheduler = new EventPreparationNotificationScheduler(
            mock(TenantSchemaRegistry.class),
            mock(TenantTransactionExecutor.class),
            service,
            publisher
        );
        ZonedDateTime now = ZonedDateTime.of(2026, 9, 6, 12, 0, 0, 0, ZoneId.of("Europe/Rome"));
        DashboardEntry entry = new DashboardEntry(
            42L,
            "Concerto",
            now.minusHours(2),
            now.minusHours(1),
            now.minusDays(1),
            PreparationStatus.BLOCKED,
            ClosureStatus.TO_CLOSE,
            3,
            0,
            Set.of("AVAILABILITY_INCOMPLETE", "PRESENCE_NOT_CONFIRMED", "FINANCE_NOT_CLOSED")
        );
        when(service.dashboardEntries(any(), any())).thenReturn(List.of(entry));

        scheduler.notifyCurrentTenant(now);

        ArgumentCaptor<NotificationCommand> commands = ArgumentCaptor.forClass(NotificationCommand.class);
        verify(publisher, times(3)).enqueue(commands.capture());
        assertThat(commands.getAllValues()).extracting(NotificationCommand::operation)
            .containsExactlyInAnyOrder("AVAILABILITY_THRESHOLD_MISSED", "PRESENCE_FOLLOW_UP", "FINANCE_FOLLOW_UP");
        assertThat(commands.getAllValues()).allSatisfy(command -> {
            assertThat(command.eventKey()).startsWith("event-preparation:42:");
            assertThat(command.targetPath()).isEqualTo("/calendar/42#preparation");
            assertThat(command.actorId()).isEqualTo(EventPreparationNotificationScheduler.SYSTEM_ACTOR);
        });
    }
}
