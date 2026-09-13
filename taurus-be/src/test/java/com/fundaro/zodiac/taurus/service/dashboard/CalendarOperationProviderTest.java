package com.fundaro.zodiac.taurus.service.dashboard;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.domain.enumeration.StateEnum;
import com.fundaro.zodiac.taurus.repository.CalendarEventsRepository;
import com.fundaro.zodiac.taurus.repository.UsersRepository;
import com.fundaro.zodiac.taurus.repository.projection.CalendarResponseProjection;
import com.fundaro.zodiac.taurus.repository.projection.UserRoleProjection;
import com.fundaro.zodiac.taurus.security.AuthoritiesConstants;
import com.fundaro.zodiac.taurus.service.dto.dashboard.DashboardOperationType;
import com.fundaro.zodiac.taurus.service.dto.eventpreparation.EventPreparationDtos.ClosureStatus;
import com.fundaro.zodiac.taurus.service.dto.eventpreparation.EventPreparationDtos.PreparationStatus;
import com.fundaro.zodiac.taurus.service.eventpreparation.EventPreparationService;
import com.fundaro.zodiac.taurus.service.eventpreparation.EventPreparationService.DashboardEntry;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CalendarOperationProviderTest {

    @Test
    void preparationBlockerReplacesTheGenericMissingResponsesOperationForTheSameEvent() {
        CalendarEventsRepository events = mock(CalendarEventsRepository.class);
        UsersRepository users = mock(UsersRepository.class);
        EventPreparationService preparation = mock(EventPreparationService.class);
        ApplicationProperties properties = new ApplicationProperties();
        CalendarOperationProvider provider = new CalendarOperationProvider(events, users, properties);
        provider.setEventPreparationService(preparation);
        ZonedDateTime now = ZonedDateTime.now(ZoneId.of("Europe/Rome"));
        DashboardEntry entry = new DashboardEntry(
            42L,
            "Concerto",
            now.plusDays(2),
            now.plusDays(2).plusHours(2),
            now.plusDays(1),
            PreparationStatus.BLOCKED,
            ClosureStatus.NOT_REQUIRED,
            1,
            0,
            Set.of("AVAILABILITY_INCOMPLETE")
        );
        UserRoleProjection participant = mock(UserRoleProjection.class);
        when(participant.getRole()).thenReturn(RoleEnum.ROLE_USER);
        when(participant.getKeycloakId()).thenReturn("participant");
        CalendarResponseProjection responses = mock(CalendarResponseProjection.class);
        when(responses.getEventId()).thenReturn(42L);
        when(responses.getEventName()).thenReturn("Concerto");
        when(responses.getState()).thenReturn(StateEnum.COMPLETE);
        when(responses.getStartDate()).thenReturn(Date.from(now.plusDays(2).toInstant()));
        when(users.findActiveUserRoles(any())).thenReturn(List.of(participant));
        when(events.summarizeResponses(eq(StateEnum.COMPLETE), any(), any(), any())).thenReturn(List.of(responses));
        when(preparation.dashboardEntries(any(), any())).thenReturn(List.of(entry));

        List<com.fundaro.zodiac.taurus.service.dto.dashboard.OperationalItemDTO> result = provider.getOperations(context(now));

        assertThat(result).extracting(value -> value.type()).contains(DashboardOperationType.EVENT_PREPARATION_BLOCKED);
        assertThat(result).extracting(value -> value.type()).doesNotContain(DashboardOperationType.CALENDAR_RESPONSES_MISSING);
    }

    private static DashboardRequestContext context(ZonedDateTime now) {
        return new DashboardRequestContext(
            "admin",
            Set.of(AuthoritiesConstants.ADMIN),
            now,
            now.getZone(),
            mock(org.springframework.security.authentication.AbstractAuthenticationToken.class)
        );
    }
}
