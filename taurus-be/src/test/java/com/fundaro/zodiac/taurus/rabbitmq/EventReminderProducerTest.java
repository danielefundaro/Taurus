package com.fundaro.zodiac.taurus.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fundaro.zodiac.taurus.domain.PushReminder;
import com.fundaro.zodiac.taurus.domain.notification.NotificationProfile;
import com.fundaro.zodiac.taurus.domain.notification.NotificationStatus;
import com.fundaro.zodiac.taurus.domain.notification.ReminderOrigin;
import com.fundaro.zodiac.taurus.repository.PushReminderRepository;
import com.fundaro.zodiac.taurus.repository.notification.NotificationProfileRepository;
import com.fundaro.zodiac.taurus.service.dto.CalendarEventsDTO;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EventReminderProducerTest {

    private static final String RECIPIENT = "recipient-1";

    private final PushReminderRepository reminderRepository = mock(PushReminderRepository.class);
    private final NotificationProfileRepository profileRepository = mock(NotificationProfileRepository.class);
    private final EventReminderProducer producer = new EventReminderProducer(reminderRepository);

    EventReminderProducerTest() {
        producer.setNotificationProfileRepository(profileRepository);
    }

    @Test
    void readsTheProfileDefaultOfTheRecipient() {
        Instant start = start();
        when(profileRepository.findByKeycloakSubjectAndDeletedFalse(anyString())).thenReturn(Optional.empty());
        when(profileRepository.findByKeycloakSubjectAndDeletedFalse(RECIPIENT)).thenReturn(Optional.of(profile(45, true)));

        producer.scheduleIfNeeded(event(start), RECIPIENT);

        PushReminder saved = savedReminder();
        assertThat(saved.getUserId()).isEqualTo(RECIPIENT);
        assertThat(saved.getSendAt()).isEqualTo(start.minus(45, ChronoUnit.MINUTES));
        assertThat(saved.getReminderOrigin()).isEqualTo(ReminderOrigin.PROFILE);
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(saved.getScheduleRevision()).isZero();
    }

    @Test
    void fallsBackToTheApplicationDefaultWhenTheRecipientHasNoProfile() {
        Instant start = start();
        when(profileRepository.findByKeycloakSubjectAndDeletedFalse(anyString())).thenReturn(Optional.empty());

        producer.scheduleIfNeeded(event(start), RECIPIENT);

        PushReminder saved = savedReminder();
        assertThat(saved.getSendAt()).isEqualTo(start.minus(30, ChronoUnit.MINUTES));
        assertThat(saved.getReminderOrigin()).isEqualTo(ReminderOrigin.APPLICATION);
    }

    @Test
    void prefersThePersonalValueOverTheProfileDefault() {
        Instant start = start();
        when(profileRepository.findByKeycloakSubjectAndDeletedFalse(RECIPIENT)).thenReturn(Optional.of(profile(45, true)));

        producer.scheduleIfNeeded(event(start), RECIPIENT, 10);

        PushReminder saved = savedReminder();
        assertThat(saved.getSendAt()).isEqualTo(start.minus(10, ChronoUnit.MINUTES));
        assertThat(saved.getReminderOrigin()).isEqualTo(ReminderOrigin.PERSONAL);
    }

    @Test
    void prefersTheValueOfTheEventOverTheProfileDefault() {
        Instant start = start();
        CalendarEventsDTO event = event(start);
        event.setReminderMinutes(90);
        when(profileRepository.findByKeycloakSubjectAndDeletedFalse(RECIPIENT)).thenReturn(Optional.of(profile(45, true)));

        producer.scheduleIfNeeded(event, RECIPIENT);

        PushReminder saved = savedReminder();
        assertThat(saved.getSendAt()).isEqualTo(start.minus(90, ChronoUnit.MINUTES));
        assertThat(saved.getReminderOrigin()).isEqualTo(ReminderOrigin.EVENT);
    }

    /**
     * L'interruttore del profilo è globale: spento, non si pianifica nulla, nemmeno un anticipo
     * scelto a mano sull'occorrenza.
     */
    @Test
    void schedulesNothingWhenTheMemberTurnedTheRemindersOff() {
        when(profileRepository.findByKeycloakSubjectAndDeletedFalse(RECIPIENT)).thenReturn(Optional.of(profile(45, false)));

        producer.scheduleIfNeeded(event(start()), RECIPIENT, 10);

        verify(reminderRepository, never()).save(any());
    }

    @Test
    void schedulesNothingWhenTheLeadTimeIsZero() {
        when(profileRepository.findByKeycloakSubjectAndDeletedFalse(RECIPIENT)).thenReturn(Optional.of(profile(0, true)));

        producer.scheduleIfNeeded(event(start()), RECIPIENT);

        verify(reminderRepository, never()).save(any());
    }

    /**
     * La ripianificazione rifà soltanto i job che ereditano il valore del profilo, e numera il
     * rifacimento: un anticipo scelto sull'occorrenza o sull'evento resta una decisione del socio.
     */
    @Test
    void remakesOnlyTheInheritedJobsAndNumbersTheRevision() {
        Instant start = start();
        when(profileRepository.findByKeycloakSubjectAndDeletedFalse(RECIPIENT)).thenReturn(Optional.of(profile(20, true)));
        when(reminderRepository.findAllByEventIdAndUserIdAndStatusAndDeletedFalse(42L, RECIPIENT, NotificationStatus.PENDING))
            .thenReturn(List.of(inherited(3), personal(9)));

        assertThat(producer.rescheduleInherited(event(start), RECIPIENT, null)).isTrue();

        verify(reminderRepository)
            .deleteAllByEventIdAndUserIdAndStatusAndReminderOriginIn(
                42L,
                RECIPIENT,
                NotificationStatus.PENDING,
                java.util.EnumSet.of(ReminderOrigin.PROFILE, ReminderOrigin.APPLICATION)
            );
        PushReminder saved = savedReminder();
        assertThat(saved.getScheduleRevision()).isEqualTo(4);
        assertThat(saved.getSendAt()).isEqualTo(start.minus(20, ChronoUnit.MINUTES));
    }

    @Test
    void leavesAnOccurrenceWithItsOwnLeadTimeAlone() {
        CalendarEventsDTO withEventValue = event(start());
        withEventValue.setReminderMinutes(15);

        assertThat(producer.rescheduleInherited(event(start()), RECIPIENT, 15)).isFalse();
        assertThat(producer.rescheduleInherited(withEventValue, RECIPIENT, null)).isFalse();

        verify(reminderRepository, never()).save(any());
        verify(reminderRepository, never())
            .deleteAllByEventIdAndUserIdAndStatusAndReminderOriginIn(any(), anyString(), any(), any());
    }

    private PushReminder savedReminder() {
        ArgumentCaptor<PushReminder> captor = ArgumentCaptor.forClass(PushReminder.class);
        verify(reminderRepository).save(captor.capture());

        return captor.getValue();
    }

    private static Instant start() {
        return Instant.now().plus(5, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
    }

    private static CalendarEventsDTO event(Instant start) {
        CalendarEventsDTO event = new CalendarEventsDTO();
        event.setId(42L);
        event.setName("Concerto di Natale");
        event.setStartDate(Date.from(start));

        return event;
    }

    private static NotificationProfile profile(int minutes, boolean remindersEnabled) {
        NotificationProfile profile = new NotificationProfile();
        profile.setDefaultCalendarReminderMinutes(minutes);
        profile.setEventRemindersEnabled(remindersEnabled);

        return profile;
    }

    private static PushReminder inherited(int revision) {
        PushReminder reminder = new PushReminder();
        reminder.setReminderOrigin(ReminderOrigin.PROFILE);
        reminder.setScheduleRevision(revision);

        return reminder;
    }

    private static PushReminder personal(int revision) {
        PushReminder reminder = new PushReminder();
        reminder.setReminderOrigin(ReminderOrigin.PERSONAL);
        reminder.setScheduleRevision(revision);

        return reminder;
    }
}
