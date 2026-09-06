package com.fundaro.zodiac.taurus.service.eventpreparation;

import static com.fundaro.zodiac.taurus.service.dto.eventpreparation.EventPreparationDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.fundaro.zodiac.taurus.domain.CalendarEvents;
import com.fundaro.zodiac.taurus.domain.CalendarEventAvailability;
import com.fundaro.zodiac.taurus.domain.Tracks;
import com.fundaro.zodiac.taurus.domain.Users;
import com.fundaro.zodiac.taurus.domain.enumeration.StateEnum;
import com.fundaro.zodiac.taurus.domain.eventpreparation.CalendarEventPreparation;
import com.fundaro.zodiac.taurus.domain.eventpreparation.PreparationProfile;
import com.fundaro.zodiac.taurus.repository.*;
import com.fundaro.zodiac.taurus.repository.eventpreparation.*;
import com.fundaro.zodiac.taurus.repository.finance.FinancialMovementRepository;
import com.fundaro.zodiac.taurus.repository.inventory.*;
import com.fundaro.zodiac.taurus.service.impl.NotificationOutboxPublisher;
import com.fundaro.zodiac.taurus.service.notification.NotificationCommand;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class EventPreparationServiceTest {
    private EventPreparationRepository preparations;
    private EventProgramEntryRepository programs;
    private EventMaterialRepository materials;
    private UsersRepository users;
    private TracksRepository tracks;
    private EventPreparationService service;
    private CalendarEvents event;

    @BeforeEach
    void setUp() {
        CalendarEventsRepository events = mock(CalendarEventsRepository.class);
        preparations = mock(EventPreparationRepository.class);
        programs = mock(EventProgramEntryRepository.class);
        materials = mock(EventMaterialRepository.class);
        users = mock(UsersRepository.class);
        tracks = mock(TracksRepository.class);
        service = new EventPreparationService(events, preparations, programs, materials, tracks, mock(InventoryItemRepository.class), mock(InventoryAssignmentRepository.class), users, mock(FinancialMovementRepository.class));
        event = new CalendarEvents();
        event.setId(42L); event.setDeleted(false); event.setName("Riunione"); event.setState(StateEnum.COMPLETE);
        event.setStartDate(Date.from(Instant.now().plusSeconds(3600))); event.setEndDate(Date.from(Instant.now().plusSeconds(7200)));
        when(events.findByIdAndDeletedFalse(42L)).thenReturn(Optional.of(event));
        when(users.findActiveKeycloakIdsByRolesIn(any())).thenReturn(List.of());
    }

    @Test
    void eventWithoutPlanIsNotConfiguredAndDoesNotBecomeAnError() {
        when(preparations.findByEvent_IdAndDeletedFalse(42L)).thenReturn(Optional.empty());
        View result = service.get(42L);
        assertThat(result.configuration()).isNull();
        assertThat(result.evaluation().preparationStatus()).isEqualTo(PreparationStatus.NOT_CONFIGURED);
        assertThat(result.evaluation().blockerCount()).isZero();
    }

    @Test
    void otherProfileWithNoOptionalChecksCanBeReady() {
        CalendarEventPreparation plan = plan();
        when(preparations.findByEvent_IdAndDeletedFalse(42L)).thenReturn(Optional.of(plan));
        when(programs.findCurrent(42L)).thenReturn(List.of()); when(materials.findCurrent(42L)).thenReturn(List.of());
        View result = service.get(42L);
        assertThat(result.evaluation().preparationStatus()).isEqualTo(PreparationStatus.READY);
        assertThat(result.evaluation().applicableChecks()).isEqualTo(1);
        assertThat(result.evaluation().completionPercent()).isEqualTo(100);
    }

    @Test
    void scoresCannotBeRequiredWithoutProgram() {
        Configuration invalid = new Configuration(PreparationProfile.OTHER, false, false, true, false, null, 1440, false, 180, false, false, false, 0);
        assertThatThrownBy(() -> service.configure(42L, invalid, null)).hasMessageContaining("Gli spartiti richiedono il programma");
        verifyNoInteractions(preparations);
    }

    @Test
    void preparationNeverChangesCalendarVisibilityState() {
        CalendarEventPreparation plan = plan();
        when(preparations.findByEvent_IdAndDeletedFalse(42L)).thenReturn(Optional.of(plan));
        when(programs.findCurrent(42L)).thenReturn(List.of()); when(materials.findCurrent(42L)).thenReturn(List.of());
        service.get(42L);
        assertThat(event.getState()).isEqualTo(StateEnum.COMPLETE);
        verify(preparations, never()).save(any());
    }

    @Test
    void missingAvailabilityBeforeDeadlineRequiresAttentionNotBlocking() {
        CalendarEventPreparation plan = plan();
        plan.setAvailabilityRequired(true); plan.setMinimumAvailableParticipants(1); plan.setAvailabilityDeadlineMinutes(0);
        when(preparations.findByEvent_IdAndDeletedFalse(42L)).thenReturn(Optional.of(plan));
        when(programs.findCurrent(42L)).thenReturn(List.of()); when(materials.findCurrent(42L)).thenReturn(List.of());
        when(users.findActiveKeycloakIdsByRolesIn(any())).thenReturn(List.of("participant"));
        View result = service.get(42L);
        assertThat(result.evaluation().preparationStatus()).isEqualTo(PreparationStatus.ATTENTION);
        assertThat(result.evaluation().warningCount()).isEqualTo(1);
        assertThat(result.evaluation().blockerCount()).isZero();
    }

    @Test
    void distantDraftIsWarningAndNeverReady() {
        event.setState(StateEnum.DRAFT);
        event.setStartDate(Date.from(Instant.now().plusSeconds(9 * 86400)));
        event.setEndDate(Date.from(Instant.now().plusSeconds(9 * 86400 + 3600)));
        CalendarEventPreparation plan = plan();
        when(preparations.findByEvent_IdAndDeletedFalse(42L)).thenReturn(Optional.of(plan));
        when(programs.findCurrent(42L)).thenReturn(List.of()); when(materials.findCurrent(42L)).thenReturn(List.of());
        View result = service.get(42L);
        assertThat(result.evaluation().preparationStatus()).isEqualTo(PreparationStatus.ATTENTION);
        assertThat(result.evaluation().issues()).extracting(Issue::code).containsExactly("EVENT_DATA");
    }

    @Test
    void personalViewContainsOnlyTheCurrentUsersAvailabilityAndNoOperationalData() {
        CalendarEventPreparation plan = plan();
        plan.setAvailabilityRequired(true);
        plan.setMinimumAvailableParticipants(2);
        plan.setMaterialsRequired(true);
        plan.setBudgetRequired(true);
        Users current = user("participant");
        Users another = user("another");
        event.getAvailabilities().add(availability(current, CalendarEventAvailability.Availability.AVAILABLE));
        event.getAvailabilities().add(availability(another, CalendarEventAvailability.Availability.UNAVAILABLE));
        when(users.findByKeycloakIdAndDeletedFalse("participant")).thenReturn(Optional.of(current));
        when(users.findActiveKeycloakIdsByRolesIn(any())).thenReturn(List.of("participant", "another"));
        when(preparations.findByEvent_IdAndDeletedFalse(42L)).thenReturn(Optional.of(plan));
        when(programs.findCurrent(42L)).thenReturn(List.of());
        when(materials.findCurrent(42L)).thenReturn(List.of());

        View result = service.getPersonal(42L, authentication("participant"), false);

        assertThat(result.configuration().materialsRequired()).isFalse();
        assertThat(result.configuration().budgetRequired()).isFalse();
        assertThat(result.materials()).isEmpty();
        assertThat(result.availability()).isEqualTo(new Availability(1, 1, 0, 0, null, result.availability().deadline()));
    }

    @Test
    void externalViewRejectsAnEventThatIsNotPublic() {
        when(users.findByKeycloakIdAndDeletedFalse("participant")).thenReturn(Optional.of(user("participant")));

        assertThatThrownBy(() -> service.getPersonal(42L, authentication("participant"), true))
            .isInstanceOf(com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException.class)
            .hasMessageContaining("Evento non trovato");
    }

    @Test
    void catalogueAndFinanceViewsDoNotExposeOtherRoleAreas() {
        CalendarEventPreparation plan = plan();
        plan.setProgramRequired(true);
        plan.setMaterialsRequired(true);
        plan.setBudgetRequired(true);
        when(preparations.findByEvent_IdAndDeletedFalse(42L)).thenReturn(Optional.of(plan));
        when(programs.findCurrent(42L)).thenReturn(List.of());
        when(materials.findCurrent(42L)).thenReturn(List.of());

        View catalogue = service.getCatalogue(42L);
        View finance = service.getFinance(42L);

        assertThat(catalogue.configuration().programRequired()).isTrue();
        assertThat(catalogue.configuration().materialsRequired()).isFalse();
        assertThat(catalogue.configuration().budgetRequired()).isFalse();
        assertThat(finance.configuration().programRequired()).isFalse();
        assertThat(finance.configuration().budgetRequired()).isTrue();
        assertThat(finance.program()).isEmpty();
        assertThat(finance.materials()).isEmpty();
    }

    @Test
    void programSaveProducesOneNotificationForAvailableUsers() {
        CalendarEventPreparation plan = plan();
        Users current = user("participant");
        event.getAvailabilities().add(availability(current, CalendarEventAvailability.Availability.AVAILABLE));
        Tracks track = new Tracks();
        track.setId(7L);
        track.setName("Marcia");
        track.setState(StateEnum.COMPLETE);
        NotificationOutboxPublisher publisher = mock(NotificationOutboxPublisher.class);
        service.setNotificationPublisher(publisher);
        when(tracks.findByIdAndDeletedFalse(7L)).thenReturn(Optional.of(track));
        when(preparations.findByEvent_IdAndDeletedFalse(42L)).thenReturn(Optional.of(plan));
        when(programs.findCurrent(42L)).thenReturn(List.of());
        when(materials.findCurrent(42L)).thenReturn(List.of());

        service.replaceProgram(42L, new ProgramRequest(List.of(new ProgramEntryRequest(7L, 120, null))), authentication("admin"));

        ArgumentCaptor<NotificationCommand> command = ArgumentCaptor.forClass(NotificationCommand.class);
        verify(publisher).enqueue(command.capture());
        assertThat(command.getValue().operation()).isEqualTo("PROGRAM_UPDATED");
        assertThat(command.getValue().targetPath()).isEqualTo("/calendar/42#preparation");
        assertThat(command.getValue().audiences()).extracting(value -> value.value()).containsExactly("participant");
    }

    private CalendarEventPreparation plan() {
        CalendarEventPreparation plan = new CalendarEventPreparation();
        plan.setEvent(event); plan.setProfile(PreparationProfile.OTHER); plan.setAvailabilityDeadlineMinutes(1440); plan.setMaterialsDeadlineMinutes(180);
        return plan;
    }

    private static Users user(String keycloakId) {
        Users user = new Users();
        user.setKeycloakId(keycloakId);
        user.setActive(true);
        return user;
    }

    private static CalendarEventAvailability availability(Users user, CalendarEventAvailability.Availability value) {
        CalendarEventAvailability availability = new CalendarEventAvailability();
        availability.setUser(user);
        availability.setAvailability(value);
        availability.setResponseDate(new Date());
        return availability;
    }

    private static JwtAuthenticationToken authentication(String subject) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject(subject).issuedAt(now).expiresAt(now.plusSeconds(300)).build();
        return new JwtAuthenticationToken(jwt);
    }
}
