package com.fundaro.zodiac.taurus.service.eventpreparation;

import static com.fundaro.zodiac.taurus.service.dto.eventpreparation.EventPreparationDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.fundaro.zodiac.taurus.domain.CalendarEvents;
import com.fundaro.zodiac.taurus.domain.enumeration.StateEnum;
import com.fundaro.zodiac.taurus.domain.eventpreparation.CalendarEventPreparation;
import com.fundaro.zodiac.taurus.domain.eventpreparation.PreparationProfile;
import com.fundaro.zodiac.taurus.repository.*;
import com.fundaro.zodiac.taurus.repository.eventpreparation.*;
import com.fundaro.zodiac.taurus.repository.finance.FinancialMovementRepository;
import com.fundaro.zodiac.taurus.repository.inventory.*;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EventPreparationServiceTest {
    private EventPreparationRepository preparations;
    private EventProgramEntryRepository programs;
    private EventMaterialRepository materials;
    private UsersRepository users;
    private EventPreparationService service;
    private CalendarEvents event;

    @BeforeEach
    void setUp() {
        CalendarEventsRepository events = mock(CalendarEventsRepository.class);
        preparations = mock(EventPreparationRepository.class);
        programs = mock(EventProgramEntryRepository.class);
        materials = mock(EventMaterialRepository.class);
        users = mock(UsersRepository.class);
        service = new EventPreparationService(events, preparations, programs, materials, mock(TracksRepository.class), mock(InventoryItemRepository.class), mock(InventoryAssignmentRepository.class), users, mock(FinancialMovementRepository.class));
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

    private CalendarEventPreparation plan() {
        CalendarEventPreparation plan = new CalendarEventPreparation();
        plan.setEvent(event); plan.setProfile(PreparationProfile.OTHER); plan.setAvailabilityDeadlineMinutes(1440); plan.setMaterialsDeadlineMinutes(180);
        return plan;
    }
}
