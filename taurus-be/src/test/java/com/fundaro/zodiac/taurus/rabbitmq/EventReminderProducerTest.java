package com.fundaro.zodiac.taurus.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fundaro.zodiac.taurus.domain.Preferences;
import com.fundaro.zodiac.taurus.domain.PushReminder;
import com.fundaro.zodiac.taurus.domain.notification.ReminderOrigin;
import com.fundaro.zodiac.taurus.repository.PreferencesRepository;
import com.fundaro.zodiac.taurus.repository.PushReminderRepository;
import com.fundaro.zodiac.taurus.service.dto.CalendarEventsDTO;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EventReminderProducerTest {

    private static final String LEGACY_KEY = "defaultReminderMinutes";
    private static final String RECIPIENT = "recipient-1";
    private static final String ACTOR = "admin-1";

    private final PushReminderRepository reminderRepository = mock(PushReminderRepository.class);
    private final PreferencesRepository preferencesRepository = mock(PreferencesRepository.class);
    private final EventReminderProducer producer = new EventReminderProducer(reminderRepository, preferencesRepository);

    @Test
    void readsTheLegacyPreferenceOfTheRecipient() {
        Instant start = Instant.now().plus(5, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        when(preferencesRepository.findFirstByUserIdAndKeyAndDeletedFalseOrderByIdDesc(anyString(), anyString()))
            .thenReturn(Optional.empty());
        when(preferencesRepository.findFirstByUserIdAndKeyAndDeletedFalseOrderByIdDesc(RECIPIENT, LEGACY_KEY))
            .thenReturn(Optional.of(preference("45")));
        when(preferencesRepository.findFirstByUserIdAndKeyAndDeletedFalseOrderByIdDesc(ACTOR, LEGACY_KEY))
            .thenReturn(Optional.of(preference("600")));

        producer.scheduleIfNeeded(event(start), RECIPIENT);

        PushReminder saved = savedReminder();
        assertThat(saved.getUserId()).isEqualTo(RECIPIENT);
        assertThat(saved.getSendAt()).isEqualTo(start.minus(45, ChronoUnit.MINUTES));
        assertThat(saved.getReminderOrigin()).isEqualTo(ReminderOrigin.APPLICATION);
    }

    @Test
    void fallsBackToTheApplicationDefaultWhenTheRecipientHasNoPreference() {
        Instant start = Instant.now().plus(5, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        when(preferencesRepository.findFirstByUserIdAndKeyAndDeletedFalseOrderByIdDesc(anyString(), anyString()))
            .thenReturn(Optional.empty());

        producer.scheduleIfNeeded(event(start), RECIPIENT);

        assertThat(savedReminder().getSendAt()).isEqualTo(start.minus(30, ChronoUnit.MINUTES));
    }

    @Test
    void keepsTheApplicationDefaultWhenTheStoredValueIsNotANumber() {
        Instant start = Instant.now().plus(5, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);
        when(preferencesRepository.findFirstByUserIdAndKeyAndDeletedFalseOrderByIdDesc(RECIPIENT, LEGACY_KEY))
            .thenReturn(Optional.of(preference("presto")));

        producer.scheduleIfNeeded(event(start), RECIPIENT);

        assertThat(savedReminder().getSendAt()).isEqualTo(start.minus(30, ChronoUnit.MINUTES));
    }

    @Test
    void prefersThePersonalValueOverTheLegacyPreference() {
        Instant start = Instant.now().plus(5, ChronoUnit.HOURS).truncatedTo(ChronoUnit.MILLIS);

        producer.scheduleIfNeeded(event(start), RECIPIENT, 10);

        PushReminder saved = savedReminder();
        assertThat(saved.getSendAt()).isEqualTo(start.minus(10, ChronoUnit.MINUTES));
        assertThat(saved.getReminderOrigin()).isEqualTo(ReminderOrigin.PERSONAL);
        verify(preferencesRepository, never()).findFirstByUserIdAndKeyAndDeletedFalseOrderByIdDesc(anyString(), anyString());
    }

    private PushReminder savedReminder() {
        ArgumentCaptor<PushReminder> captor = ArgumentCaptor.forClass(PushReminder.class);
        verify(reminderRepository).save(captor.capture());

        return captor.getValue();
    }

    private static CalendarEventsDTO event(Instant start) {
        CalendarEventsDTO event = new CalendarEventsDTO();
        event.setId(42L);
        event.setName("Concerto di Natale");
        event.setStartDate(Date.from(start));

        return event;
    }

    private static Preferences preference(String value) {
        Preferences preference = new Preferences();
        preference.setKey(LEGACY_KEY);
        preference.setValue(value);

        return preference;
    }
}
