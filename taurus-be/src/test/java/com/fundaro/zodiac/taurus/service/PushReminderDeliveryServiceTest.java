package com.fundaro.zodiac.taurus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.PushReminder;
import com.fundaro.zodiac.taurus.domain.notification.NotificationPushMode;
import com.fundaro.zodiac.taurus.domain.notification.NotificationPushPreview;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSource;
import com.fundaro.zodiac.taurus.domain.notification.NotificationStatus;
import com.fundaro.zodiac.taurus.multitenancy.TenantContext;
import com.fundaro.zodiac.taurus.repository.PushReminderRepository;
import com.fundaro.zodiac.taurus.service.notification.NotificationPreferenceDecision;
import com.fundaro.zodiac.taurus.service.notification.PushDeliveryResult;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Consegna dei promemoria evento, un job per volta: è il comportamento che prima stava nello
 * scheduler dei soli promemoria e che ora segue la forma delle altre consegne push.
 */
class PushReminderDeliveryServiceTest {

    private static final ZoneId ROME = ZoneId.of("Europe/Rome");

    private final PushReminderRepository reminderRepository = mock(PushReminderRepository.class);
    private final PushService pushService = mock(PushService.class);
    private final NotificationPreferenceResolver preferenceResolver = mock(NotificationPreferenceResolver.class);
    private PushReminderDeliveryService service;
    private TenantContext.Scope tenantScope;

    @BeforeEach
    void setUp() {
        tenantScope = TenantContext.use("BMCDG");
        service = new PushReminderDeliveryService(reminderRepository, pushService, new ApplicationProperties());
        service.setPreferenceResolver(preferenceResolver);
    }

    @AfterEach
    void tearDown() {
        tenantScope.close();
    }

    @Test
    void sendsCalendarReminderToTheApplicationEventRoute() {
        PushReminder reminder = ready(reminder());
        enabled();
        when(pushService.sendToUserNow(eq("user-1"), eq("BMCDG"), anyString(), anyString(), eq("/calendar/42")))
            .thenReturn(new PushDeliveryResult(1, 0, 0, 0, 1));

        service.process(7L);

        verify(pushService).sendToUserNow(eq("user-1"), eq("BMCDG"), eq("Promemoria evento"), anyString(), eq("/calendar/42"));
        assertThat(reminder.getStatus()).isEqualTo(NotificationStatus.DELIVERED);
        assertThat(reminder.getDeliveredAt()).isNotNull();
        verify(reminderRepository).save(reminder);
    }

    @Test
    void closesAReminderRefusedForGoodSoThatItIsNotSentAgainEveryMinute() {
        PushReminder reminder = ready(reminder());
        enabled();
        when(pushService.sendToUserNow(eq("user-1"), eq("BMCDG"), anyString(), anyString(), eq("/calendar/42")))
            .thenReturn(new PushDeliveryResult(0, 0, 0, 1, 1));

        service.process(7L);

        assertThat(reminder.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(reminder.getNextAttemptAt()).isNull();
    }

    @Test
    void skipsAReminderLateAfterTheStartOfTheEvent() {
        PushReminder reminder = reminder();
        reminder.setSendAt(Instant.now().minusSeconds(7200));
        reminder.setEventStartAt(Instant.now().minusSeconds(60));
        ready(reminder);

        service.process(7L);

        verifyNoInteractions(pushService);
        assertThat(reminder.getStatus()).isEqualTo(NotificationStatus.SKIPPED);
        assertThat(reminder.getSkipReason()).isEqualTo("EVENT_STARTED");
    }

    /** Il lock pessimista è la difesa contro due repliche: un job già chiuso non va riaperto. */
    @Test
    void ignoresAJobThatAnotherReplicaHasAlreadyClosed() {
        PushReminder reminder = reminder();
        reminder.setStatus(NotificationStatus.DELIVERED);
        ready(reminder);

        service.process(7L);

        verifyNoInteractions(pushService);
        verify(reminderRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void skipsAReminderOfSomebodyWhoTurnedThemOff() {
        PushReminder reminder = ready(reminder());
        when(preferenceResolver.resolve(eq(NotificationSource.CALENDAR), any(), eq(Set.of("user-1"))))
            .thenReturn(Map.of("user-1", preference(false, NotificationPushPreview.FULL, LocalTime.of(22, 0), LocalTime.of(7, 0), false)));

        service.process(7L);

        verifyNoInteractions(pushService);
        assertThat(reminder.getStatus()).isEqualTo(NotificationStatus.SKIPPED);
        assertThat(reminder.getSkipReason()).isEqualTo("REMINDERS_DISABLED");
    }

    /**
     * Dentro le ore di silenzio il promemoria si sposta all'uscita, ma solo se l'evento non è già
     * iniziato: rimandare un promemoria a evento concluso non serve a nessuno.
     */
    @Test
    void defersInsideQuietHoursAndGivesUpWhenTheEventStartsFirst() {
        PushReminder deferrable = reminder();
        deferrable.setEventStartAt(Instant.now().plus(3, ChronoUnit.DAYS));
        ready(deferrable);
        quietForOneMoreHour();

        service.process(7L);

        verifyNoInteractions(pushService);
        assertThat(deferrable.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(deferrable.getSendAt()).isAfter(Instant.now().plusSeconds(30 * 60));

        PushReminder tooLate = reminder();
        tooLate.setEventStartAt(Instant.now().plusSeconds(30 * 60));
        ready(tooLate);

        service.process(7L);

        verifyNoInteractions(pushService);
        assertThat(tooLate.getStatus()).isEqualTo(NotificationStatus.SKIPPED);
        assertThat(tooLate.getSkipReason()).isEqualTo("EVENT_STARTED");
    }

    @Test
    void tellsNothingOfTheEventWithThePrivatePreview() {
        Instant now = Instant.parse("2026-10-26T10:00:00Z");

        assertThat(PushReminderDeliveryService.reminderBody(NotificationPushPreview.PRIVATE, "Concerto di Natale", now.plusSeconds(1800), now))
            .isEqualTo("Hai un promemoria per un evento");
    }

    /** Con l'anteprima completa l'anticipo è l'informazione per cui il promemoria esiste. */
    @Test
    void writesTheLeadTimeInWordsWithTheFullPreview() {
        Instant now = Instant.parse("2026-10-26T10:00:00Z");

        assertThat(PushReminderDeliveryService.reminderBody(NotificationPushPreview.FULL, "Prova", now.plusSeconds(1800), now))
            .isEqualTo("L'evento \"Prova\" sta per iniziare tra 30 minuti");
        assertThat(PushReminderDeliveryService.leadTime(now.plusSeconds(60), now)).isEqualTo("tra 1 minuto");
        assertThat(PushReminderDeliveryService.leadTime(now.plusSeconds(3600), now)).isEqualTo("tra 1 ora");
        assertThat(PushReminderDeliveryService.leadTime(now.plusSeconds(3600 + 60), now)).isEqualTo("tra 1 ora e 1 minuto");
        assertThat(PushReminderDeliveryService.leadTime(now.plusSeconds(2 * 3600 + 1500), now)).isEqualTo("tra 2 ore e 25 minuti");
        assertThat(PushReminderDeliveryService.leadTime(now.plusSeconds(10), now)).isEqualTo("adesso");
        assertThat(PushReminderDeliveryService.leadTime(null, now)).isEmpty();
        assertThat(PushReminderDeliveryService.reminderBody(NotificationPushPreview.FULL, "Prova", null, now))
            .isEqualTo("L'evento \"Prova\" sta per iniziare");
    }

    private PushReminder ready(PushReminder reminder) {
        when(reminderRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(reminder));
        return reminder;
    }

    private void enabled() {
        when(preferenceResolver.resolve(eq(NotificationSource.CALENDAR), any(), eq(Set.of("user-1"))))
            .thenReturn(Map.of("user-1", preference(true, NotificationPushPreview.FULL, LocalTime.of(22, 0), LocalTime.of(7, 0), false)));
    }

    /**
     * Una finestra di silenzio che contiene adesso e finisce fra un'ora: l'asserzione non dipende
     * dall'ora in cui la suite gira.
     */
    private void quietForOneMoreHour() {
        LocalTime now = ZonedDateTime.now(ROME).toLocalTime();
        when(preferenceResolver.resolve(eq(NotificationSource.CALENDAR), any(), eq(Set.of("user-1"))))
            .thenReturn(Map.of("user-1", preference(true, NotificationPushPreview.FULL, now.minusHours(1), now.plusHours(1), true)));
    }

    private static PushReminder reminder() {
        PushReminder reminder = new PushReminder();
        reminder.setEventId(42L);
        reminder.setEventName("Prova");
        reminder.setUserId("user-1");
        reminder.setStatus(NotificationStatus.PENDING);
        reminder.setSendAt(Instant.now().minusSeconds(60));
        reminder.setEventStartAt(Instant.now().plusSeconds(3600));
        return reminder;
    }

    private static NotificationPreferenceDecision preference(
        boolean remindersEnabled,
        NotificationPushPreview preview,
        LocalTime start,
        LocalTime end,
        boolean quietEnabled
    ) {
        return new NotificationPreferenceDecision(
            "user-1",
            true,
            NotificationPushMode.IMMEDIATE,
            remindersEnabled,
            ZoneId.of("Europe/Rome"),
            LocalTime.of(8, 0),
            quietEnabled,
            start,
            end,
            null,
            preview,
            false
        );
    }
}
