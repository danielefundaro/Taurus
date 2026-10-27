package com.fundaro.zodiac.taurus.service.dashboard;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.domain.enumeration.StateEnum;
import com.fundaro.zodiac.taurus.domain.enumeration.TenantFeature;
import com.fundaro.zodiac.taurus.repository.CalendarEventsRepository;
import com.fundaro.zodiac.taurus.repository.UsersRepository;
import com.fundaro.zodiac.taurus.repository.projection.CalendarAttentionProjection;
import com.fundaro.zodiac.taurus.repository.projection.CalendarResponseProjection;
import com.fundaro.zodiac.taurus.repository.projection.UserRoleProjection;
import com.fundaro.zodiac.taurus.security.AuthoritiesConstants;
import com.fundaro.zodiac.taurus.service.TenantFeatureService;
import com.fundaro.zodiac.taurus.service.dashboard.CalendarOperationProvider.CalendarAttention;
import com.fundaro.zodiac.taurus.service.dto.dashboard.DashboardOperationType;
import com.fundaro.zodiac.taurus.service.dto.dashboard.DashboardSeverity;
import com.fundaro.zodiac.taurus.service.dto.dashboard.OperationalItemDTO;
import com.fundaro.zodiac.taurus.service.dto.eventpreparation.EventPreparationDtos.ClosureStatus;
import com.fundaro.zodiac.taurus.service.dto.eventpreparation.EventPreparationDtos.PreparationStatus;
import com.fundaro.zodiac.taurus.service.eventpreparation.EventPreparationService;
import com.fundaro.zodiac.taurus.service.eventpreparation.EventPreparationService.DashboardEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class CalendarOperationProviderTest {

    private final ZonedDateTime now = ZonedDateTime.of(2026, 10, 27, 10, 0, 0, 0, ZoneId.of("Europe/Rome"));
    private CalendarEventsRepository events;
    private UsersRepository users;
    private EventPreparationService preparation;
    private CalendarOperationProvider provider;

    @BeforeEach
    void createProvider() {
        events = mock(CalendarEventsRepository.class);
        users = mock(UsersRepository.class);
        preparation = mock(EventPreparationService.class);
        provider = new CalendarOperationProvider(events, users, new ApplicationProperties());
        provider.setEventPreparationService(preparation);
    }

    @Test
    void preparationBlockerReplacesTheGenericMissingResponsesOperationForTheSameEvent() {
        participants("participant");
        responses(StateEnum.COMPLETE, response(42L, now.plusDays(2), 0));
        preparationEntries(entry(42L, now.plusDays(2), PreparationStatus.BLOCKED, ClosureStatus.NOT_REQUIRED, "AVAILABILITY_INCOMPLETE"));

        List<OperationalItemDTO> result = provider.getOperations(context(AuthoritiesConstants.ADMIN));

        assertThat(result).extracting(OperationalItemDTO::type).contains(DashboardOperationType.EVENT_PREPARATION_BLOCKED);
        assertThat(result).extracting(OperationalItemDTO::type).doesNotContain(DashboardOperationType.CALENDAR_RESPONSES_MISSING);
    }

    @Test
    void anEventBetweenSevenAndFourteenDaysWithTheSoleAvailabilityWarningStaysAmongTheMissingResponses() {
        participants("participant");
        responses(StateEnum.COMPLETE, response(43L, now.plusDays(10), 0));
        preparationEntries(entry(43L, now.plusDays(10), PreparationStatus.ATTENTION, ClosureStatus.NOT_REQUIRED, "AVAILABILITY_INCOMPLETE"));

        List<OperationalItemDTO> result = provider.getOperations(context(AuthoritiesConstants.ADMIN));

        // Oltre i sette giorni di EVENT_PREPARATION_ATTENTION l'evento non è in nessuna riga di preparazione: toglierlo
        // anche dalle risposte mancanti lo farebbe sparire dalla dashboard.
        assertThat(result).extracting(OperationalItemDTO::type)
            .containsExactly(DashboardOperationType.CALENDAR_RESPONSES_MISSING)
            .doesNotContain(DashboardOperationType.EVENT_PREPARATION_ATTENTION);
        assertThat(result.get(0).count()).isEqualTo(1);
        assertThat(result.get(0).relatedCount()).isEqualTo(1L);
    }

    @Test
    void anAttentionWithinSevenDaysStillReplacesTheMissingResponses() {
        participants("participant");
        responses(StateEnum.COMPLETE, response(44L, now.plusDays(5), 0));
        preparationEntries(entry(44L, now.plusDays(5), PreparationStatus.ATTENTION, ClosureStatus.NOT_REQUIRED, "AVAILABILITY_INCOMPLETE"));

        List<OperationalItemDTO> result = provider.getOperations(context(AuthoritiesConstants.ADMIN));

        assertThat(result).extracting(OperationalItemDTO::type).containsExactly(DashboardOperationType.EVENT_PREPARATION_ATTENTION);
    }

    @Test
    void listsTheEventsOfTheTwoPreparationRowsNearestFirst() {
        preparationEntries(
            entry(1L, now.plusDays(3), PreparationStatus.BLOCKED, ClosureStatus.NOT_REQUIRED),
            entry(2L, now.plusDays(2), PreparationStatus.ATTENTION, ClosureStatus.NOT_REQUIRED),
            entry(3L, now.plusDays(10), PreparationStatus.ATTENTION, ClosureStatus.NOT_REQUIRED),
            entry(4L, now.minusDays(5), PreparationStatus.READY, ClosureStatus.TO_CLOSE),
            entry(5L, now.plusDays(1), PreparationStatus.READY, ClosureStatus.NOT_REQUIRED)
        );

        assertThat(provider.attentionEventIds(context(AuthoritiesConstants.ADMIN), CalendarAttention.EVENT_PREPARATION)).containsExactly(2L, 1L);
        assertThat(provider.attentionEventIds(context(AuthoritiesConstants.ADMIN), CalendarAttention.EVENT_FOLLOW_UP)).containsExactly(4L);
    }

    @Test
    void theFilterCountsTheSameEventsAsTheDashboardRows() {
        preparationEntries(
            entry(1L, now.plusDays(3), PreparationStatus.BLOCKED, ClosureStatus.NOT_REQUIRED),
            entry(2L, now.plusDays(2), PreparationStatus.ATTENTION, ClosureStatus.NOT_REQUIRED),
            entry(4L, now.minusDays(5), PreparationStatus.READY, ClosureStatus.TO_CLOSE)
        );

        List<OperationalItemDTO> rows = provider.getOperations(context(AuthoritiesConstants.ADMIN));
        long preparationCount = rows.stream()
            .filter(row -> row.targetPath().equals("/calendar?attention=event-preparation"))
            .mapToLong(OperationalItemDTO::count)
            .sum();
        long followUpCount = rows.stream()
            .filter(row -> row.targetPath().equals("/calendar?attention=event-follow-up"))
            .mapToLong(OperationalItemDTO::count)
            .sum();

        assertThat(provider.attentionEventIds(context(AuthoritiesConstants.ADMIN), CalendarAttention.EVENT_PREPARATION)).hasSize((int) preparationCount);
        assertThat(provider.attentionEventIds(context(AuthoritiesConstants.ADMIN), CalendarAttention.EVENT_FOLLOW_UP)).hasSize((int) followUpCount);
    }

    @Test
    void theFilterIsEmptyForNonAdministratorsAndWithoutTheFeature() {
        preparationEntries(entry(1L, now.plusDays(3), PreparationStatus.BLOCKED, ClosureStatus.NOT_REQUIRED));

        assertThat(provider.attentionEventIds(context(AuthoritiesConstants.USER), CalendarAttention.EVENT_PREPARATION)).isEmpty();
        assertThat(provider.attentionEventIds(context(AuthoritiesConstants.TREASURER), CalendarAttention.EVENT_FOLLOW_UP)).isEmpty();

        TenantFeatureService features = mock(TenantFeatureService.class);
        when(features.isEnabled(TenantFeature.EVENT_PREPARATION)).thenReturn(false);
        provider.setTenantFeatureService(features);

        assertThat(provider.attentionEventIds(context(AuthoritiesConstants.ADMIN), CalendarAttention.EVENT_PREPARATION)).isEmpty();
        verifyNoInteractions(preparation);
    }

    @Test
    void translatesOnlyTheTwoSupportedParameters() {
        assertThat(CalendarAttention.fromParameter("event-preparation")).contains(CalendarAttention.EVENT_PREPARATION);
        assertThat(CalendarAttention.fromParameter("event-follow-up")).contains(CalendarAttention.EVENT_FOLLOW_UP);
        assertThat(CalendarAttention.fromParameter("missing-availability")).isEmpty();
        assertThat(CalendarAttention.fromParameter(null)).isEmpty();
    }

    @Test
    void anExternalUserIsAskedOnlyAboutPublicEvents() {
        missingAvailability(1, now.plusDays(3));

        provider.getOperations(context(AuthoritiesConstants.USER_EXTERNAL));

        verify(events).summarizeMissingAvailability(eq("subject"), eq(EnumSet.of(StateEnum.PUBLIC)), any(), any());
    }

    @Test
    void aMemberIsAskedAboutCompleteAndPublicEvents() {
        missingAvailability(1, now.plusDays(3));

        provider.getOperations(context(AuthoritiesConstants.USER));

        verify(events).summarizeMissingAvailability(eq("subject"), eq(EnumSet.of(StateEnum.COMPLETE, StateEnum.PUBLIC)), any(), any());
    }

    @Test
    void aTreasurerAloneHasNoCalendarOperationAndRunsNoQuery() {
        assertThat(provider.getOperations(context(AuthoritiesConstants.TREASURER))).isEmpty();
        verifyNoInteractions(events, users, preparation);
    }

    @Test
    void anEventStartingInExactlyTwentyFourHoursIsDanger() {
        missingAvailability(2, now.plusHours(24));

        OperationalItemDTO item = provider.getOperations(context(AuthoritiesConstants.USER)).get(0);

        assertThat(item.type()).isEqualTo(DashboardOperationType.CALENDAR_AVAILABILITY_REQUIRED);
        assertThat(item.severity()).isEqualTo(DashboardSeverity.DANGER);
        assertThat(item.count()).isEqualTo(2);
        assertThat(item.targetPath()).isEqualTo("/calendar?attention=my-missing-availability");
    }

    @Test
    void anEventStartingOneSecondAfterTwentyFourHoursIsWarning() {
        missingAvailability(1, now.plusHours(24).plusSeconds(1));

        assertThat(provider.getOperations(context(AuthoritiesConstants.USER)).get(0).severity()).isEqualTo(DashboardSeverity.WARNING);
    }

    @Test
    void anAdministratorWhoIsAlsoAMemberGetsBothRowsOnce() {
        missingAvailability(1, now.plusDays(3));
        participants("participant");
        responses(StateEnum.COMPLETE, response(7L, now.plusDays(3), 0));

        List<OperationalItemDTO> result = provider.getOperations(context(AuthoritiesConstants.ADMIN, AuthoritiesConstants.USER));

        assertThat(result).extracting(OperationalItemDTO::type)
            .containsExactlyInAnyOrder(DashboardOperationType.CALENDAR_AVAILABILITY_REQUIRED, DashboardOperationType.CALENDAR_RESPONSES_MISSING);
    }

    @Test
    void theAdministrativeRowCountsEventsAndNamesOnlyTheNearest() {
        participants("first", "second");
        responses(StateEnum.COMPLETE, response(8L, now.plusDays(4), 1), response(9L, now.plusDays(2), 0), response(10L, now.plusDays(5), 2));

        OperationalItemDTO item = provider.getOperations(context(AuthoritiesConstants.ADMIN)).get(0);

        assertThat(item.count()).isEqualTo(2);
        assertThat(item.relatedCount()).isEqualTo(3L);
        assertThat(item.description()).isEqualTo("3 risposte mancanti; il primo evento è “Evento 9”.");
        assertThat(item.description()).doesNotContain("first", "second");
    }

    private void participants(String... keycloakIds) {
        List<UserRoleProjection> audience = java.util.Arrays.stream(keycloakIds).map(id -> {
            UserRoleProjection participant = mock(UserRoleProjection.class);
            when(participant.getRole()).thenReturn(RoleEnum.ROLE_USER);
            when(participant.getKeycloakId()).thenReturn(id);
            return participant;
        }).toList();
        when(users.findActiveUserRoles(any())).thenReturn(audience);
    }

    private void responses(StateEnum state, CalendarResponseProjection... summaries) {
        when(events.summarizeResponses(eq(state), any(), any(), any())).thenReturn(List.of(summaries));
    }

    private CalendarResponseProjection response(Long eventId, ZonedDateTime startDate, long responseCount) {
        CalendarResponseProjection responses = mock(CalendarResponseProjection.class);
        when(responses.getEventId()).thenReturn(eventId);
        when(responses.getEventName()).thenReturn("Evento " + eventId);
        when(responses.getState()).thenReturn(StateEnum.COMPLETE);
        when(responses.getStartDate()).thenReturn(Date.from(startDate.toInstant()));
        when(responses.getResponseCount()).thenReturn(responseCount);
        return responses;
    }

    private void missingAvailability(long eventCount, ZonedDateTime earliest) {
        CalendarAttentionProjection summary = mock(CalendarAttentionProjection.class);
        when(summary.getEventCount()).thenReturn(eventCount);
        when(summary.getEarliestStartDate()).thenReturn(Date.from(earliest.toInstant()));
        when(events.summarizeMissingAvailability(any(), any(), any(), any())).thenReturn(summary);
    }

    private void preparationEntries(DashboardEntry... entries) {
        when(preparation.dashboardEntries(any(), any())).thenReturn(List.of(entries));
    }

    private static DashboardEntry entry(Long eventId, ZonedDateTime startsAt, PreparationStatus status, ClosureStatus closure, String... issues) {
        return new DashboardEntry(
            eventId,
            "Evento " + eventId,
            startsAt,
            startsAt.plusHours(2),
            startsAt.minusDays(1),
            status,
            closure,
            status == PreparationStatus.BLOCKED ? 1 : 0,
            status == PreparationStatus.ATTENTION ? 1 : 0,
            Set.of(issues)
        );
    }

    private DashboardRequestContext context(String... authorities) {
        return new DashboardRequestContext(
            "subject",
            Set.of(authorities),
            now,
            now.getZone(),
            mock(org.springframework.security.authentication.AbstractAuthenticationToken.class)
        );
    }
}
