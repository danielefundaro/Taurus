package com.fundaro.zodiac.taurus.service.eventpreparation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaRegistry;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import com.fundaro.zodiac.taurus.service.dto.eventpreparation.EventPreparationDtos.ClosureStatus;
import com.fundaro.zodiac.taurus.service.dto.eventpreparation.EventPreparationDtos.PreparationStatus;
import com.fundaro.zodiac.taurus.service.eventpreparation.EventPreparationService.DashboardEntry;
import com.fundaro.zodiac.taurus.service.eventpreparation.EventPreparationService.ObsoleteConfirmation;
import com.fundaro.zodiac.taurus.service.impl.NotificationOutboxPublisher;
import com.fundaro.zodiac.taurus.service.notification.NotificationCommand;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EventPreparationNotificationSchedulerTest {
    private static final ZonedDateTime NOW = ZonedDateTime.of(2026, 9, 6, 12, 0, 0, 0, ZoneId.of("Europe/Rome"));

    private EventPreparationService service;
    private NotificationOutboxPublisher publisher;
    private SimpleMeterRegistry registry;
    private EventPreparationNotificationScheduler scheduler;

    @BeforeEach
    void setUp() {
        service = mock(EventPreparationService.class);
        publisher = mock(NotificationOutboxPublisher.class);
        registry = new SimpleMeterRegistry();
        scheduler = new EventPreparationNotificationScheduler(mock(TenantSchemaRegistry.class), mock(TenantTransactionExecutor.class), service, publisher);
        scheduler.setMetrics(new EventPreparationMetrics(registry));
    }

    @Test
    void emitsOneDeterministicNotificationForEachExpiredPreparationArea() {
        DashboardEntry entry = entry(NOW.minusHours(2), NOW.minusHours(1), Set.of("AVAILABILITY_INCOMPLETE", "PRESENCE_NOT_CONFIRMED", "FINANCE_NOT_CLOSED"), List.of());
        when(service.scheduledEntries(any(), any())).thenReturn(List.of(entry));

        scheduler.notifyCurrentTenant(NOW);

        ArgumentCaptor<NotificationCommand> commands = ArgumentCaptor.forClass(NotificationCommand.class);
        verify(publisher, times(3)).enqueue(commands.capture());
        assertThat(commands.getAllValues()).extracting(NotificationCommand::operation)
            .containsExactlyInAnyOrder("AVAILABILITY_THRESHOLD_MISSED", "PRESENCE_FOLLOW_UP", "FINANCE_FOLLOW_UP");
        assertThat(commands.getAllValues()).allSatisfy(command -> {
            assertThat(command.eventKey()).startsWith("event-preparation:42:");
            assertThat(command.targetPath()).isEqualTo("/calendar/42#preparation");
            assertThat(command.actorId()).isEqualTo(EventPreparationNotificationScheduler.SYSTEM_ACTOR);
        });
        // The keys of the deadlines keep the instant they had before the material notifications.
        assertThat(commands.getAllValues()).extracting(NotificationCommand::eventKey)
            .contains("event-preparation:42:PRESENCE_FOLLOW_UP:" + NOW.minusHours(1).toInstant());
        verify(service, never()).dashboardEntries(any(), any());
    }

    @Test
    void notifiesAnInvalidatedMaterialConfirmationOncePerObsoleteHashAndCountsItWhenNew() {
        ObsoleteConfirmation obsolete = new ObsoleteConfirmation(9L, "Leggio", "a".repeat(64));
        DashboardEntry entry = entry(NOW.plusDays(1), NOW.plusDays(1).plusHours(2), Set.of("MATERIAL_NOT_READY"), List.of(obsolete));
        when(service.scheduledEntries(any(), any())).thenReturn(List.of(entry));
        when(publisher.enqueue(any())).thenReturn(true, false);

        scheduler.notifyCurrentTenant(NOW);
        scheduler.notifyCurrentTenant(NOW.plusMinutes(1));

        ArgumentCaptor<NotificationCommand> commands = ArgumentCaptor.forClass(NotificationCommand.class);
        verify(publisher, times(2)).enqueue(commands.capture());
        NotificationCommand command = commands.getValue();
        assertThat(command.operation()).isEqualTo("MATERIAL_CONFIRMATION_INVALIDATED");
        assertThat(command.eventKey()).isEqualTo("event-preparation:42:MATERIAL_CONFIRMATION_INVALIDATED:9:" + "a".repeat(64));
        assertThat(command.eventKey().length()).isLessThanOrEqualTo(160);
        assertThat(command.message()).contains("Leggio").contains("Concerto");
        assertThat(command.targetPath()).isEqualTo("/calendar/42#preparation-materials");
        assertThat(command.audiences()).extracting(value -> value.value()).containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_SUPER_ADMIN");
        assertThat(registry.counter("taurus.event_preparation.confirmation.invalidated", "area", "material").count()).isEqualTo(1);
    }

    @Test
    void anEndedEventNoLongerNotifiesItsMaterials() {
        ObsoleteConfirmation obsolete = new ObsoleteConfirmation(9L, "Leggio", "b".repeat(64));
        when(service.scheduledEntries(any(), any())).thenReturn(List.of(entry(NOW.minusHours(3), NOW.minusHours(1), Set.of(), List.of(obsolete))));

        scheduler.notifyCurrentTenant(NOW);

        verify(publisher, never()).enqueue(any());
    }

    private static DashboardEntry entry(ZonedDateTime dueAt, ZonedDateTime endedAt, Set<String> issues, List<ObsoleteConfirmation> obsolete) {
        return new DashboardEntry(42L, "Concerto", dueAt, endedAt, NOW.minusDays(1), PreparationStatus.BLOCKED, ClosureStatus.TO_CLOSE, 3, 0, issues, obsolete);
    }
}
