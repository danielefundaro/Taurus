package com.fundaro.zodiac.taurus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.Users;
import com.fundaro.zodiac.taurus.domain.notification.NotificationProfile;
import com.fundaro.zodiac.taurus.domain.notification.NotificationPushMode;
import com.fundaro.zodiac.taurus.domain.notification.NotificationPushPreview;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSource;
import com.fundaro.zodiac.taurus.repository.CalendarEventsRepository;
import com.fundaro.zodiac.taurus.repository.UsersRepository;
import com.fundaro.zodiac.taurus.repository.notification.NotificationProfileRepository;
import com.fundaro.zodiac.taurus.rabbitmq.EventReminderProducer;
import com.fundaro.zodiac.taurus.service.dto.notification.NotificationCategoryPreferenceDTO;
import com.fundaro.zodiac.taurus.service.dto.notification.NotificationPreferencesDTO;
import com.fundaro.zodiac.taurus.service.dto.notification.NotificationQuietHoursDTO;
import com.fundaro.zodiac.taurus.service.mapper.CalendarEventsMapper;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class NotificationPreferencesServiceTest {

    private final NotificationProfileRepository repository = mock(NotificationProfileRepository.class);
    private final TenantTimeZoneService tenantTimeZoneService = mock(TenantTimeZoneService.class);
    private final UsersRepository usersRepository = mock(UsersRepository.class);
    private final CalendarEventsRepository calendarEventsRepository = mock(CalendarEventsRepository.class);
    private final EventReminderProducer eventReminderProducer = mock(EventReminderProducer.class);
    private NotificationPreferencesService service;

    @BeforeEach
    void setUp() {
        service = new NotificationPreferencesService(
            repository,
            usersRepository,
            tenantTimeZoneService,
            calendarEventsRepository,
            mock(CalendarEventsMapper.class),
            eventReminderProducer,
            new ApplicationProperties()
        );
    }

    @Test
    void returnsCompleteSafeDefaultsWithoutPersisting() {
        when(repository.findByUserKeycloakIdAndDeletedFalse("user-1")).thenReturn(Optional.empty());
        when(tenantTimeZoneService.currentZoneId()).thenReturn(ZoneId.of("Europe/Rome"));

        NotificationPreferencesDTO result = service.get(authentication());

        assertThat(result.version()).isNull();
        assertThat(result.timeZone()).isEqualTo("Europe/Rome");
        assertThat(result.defaultCalendarReminderMinutes()).isEqualTo(30);
        assertThat(result.pushPreview()).isEqualTo(NotificationPushPreview.PRIVATE);
        assertThat(result.categories()).hasSize(NotificationSource.values().length)
            .allSatisfy(category -> {
                assertThat(category.inAppEnabled()).isTrue();
                assertThat(category.pushMode()).isEqualTo(NotificationPushMode.OFF);
            });
    }

    @Test
    void rejectsADigestInsideCrossMidnightQuietHours() {
        var categories = Arrays.stream(NotificationSource.values())
            .map(source -> new NotificationCategoryPreferenceDTO(source, true, NotificationPushMode.DAILY_DIGEST))
            .toList();
        NotificationPreferencesDTO request = new NotificationPreferencesDTO(
            null,
            "Europe/Rome",
            true,
            30,
            new NotificationQuietHoursDTO(true, LocalTime.of(22, 0), LocalTime.of(7, 0)),
            null,
            LocalTime.of(6, 30),
            NotificationPushPreview.PRIVATE,
            categories
        );

        assertThatThrownBy(() -> service.save(request, authentication()))
            .isInstanceOf(RequestAlertException.class)
            .extracting(error -> ((RequestAlertException) error).getErrorKey())
            .isEqualTo("digest.quietHours");
    }

    private static JwtAuthenticationToken authentication() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("user-1").claim("tenant", "tenant-a").build();
        return new JwtAuthenticationToken(jwt);
    }
    @Test
    void rejectsAnInvalidTimeZone() {
        assertThatThrownBy(() -> service.save(request(builder -> builder.timeZone = "Mars/Olympus"), authentication()))
            .isInstanceOf(RequestAlertException.class)
            .extracting(error -> ((RequestAlertException) error).getErrorKey())
            .isEqualTo("timeZone.invalid");
    }

    @Test
    void rejectsAReminderOutsideTheAllowedRange() {
        assertThatThrownBy(() -> service.save(request(builder -> builder.reminderMinutes = 1441), authentication()))
            .isInstanceOf(RequestAlertException.class)
            .extracting(error -> ((RequestAlertException) error).getErrorKey())
            .isEqualTo("reminder.invalid");
        assertThatThrownBy(() -> service.save(request(builder -> builder.reminderMinutes = -1), authentication()))
            .isInstanceOf(RequestAlertException.class)
            .extracting(error -> ((RequestAlertException) error).getErrorKey())
            .isEqualTo("reminder.invalid");
    }

    @Test
    void acceptsZeroAsAValidReminderThatDisablesTheLevel() {
        activeUser();
        when(repository.findByUserKeycloakIdAndDeletedFalse("user-1")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(NotificationProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.save(request(builder -> builder.reminderMinutes = 0), authentication());

        assertThat(result.defaultCalendarReminderMinutes()).isZero();
    }

    @Test
    void rejectsQuietHoursWithIdenticalBounds() {
        assertThatThrownBy(() -> service.save(
            request(builder -> builder.quiet = new NotificationQuietHoursDTO(true, LocalTime.of(22, 0), LocalTime.of(22, 0))),
            authentication()
        ))
            .isInstanceOf(RequestAlertException.class)
            .extracting(error -> ((RequestAlertException) error).getErrorKey())
            .isEqualTo("quietHours.invalid");
    }

    @Test
    void rejectsAPauseLongerThanTheConfiguredMaximum() {
        assertThatThrownBy(() -> service.save(
            request(builder -> builder.pausedUntil = ZonedDateTime.now().plusDays(31)),
            authentication()
        ))
            .isInstanceOf(RequestAlertException.class)
            .extracting(error -> ((RequestAlertException) error).getErrorKey())
            .isEqualTo("pushPause.invalid");
    }

    @Test
    void rejectsAPauseAlreadyInThePast() {
        assertThatThrownBy(() -> service.save(
            request(builder -> builder.pausedUntil = ZonedDateTime.now().minusMinutes(1)),
            authentication()
        ))
            .isInstanceOf(RequestAlertException.class)
            .extracting(error -> ((RequestAlertException) error).getErrorKey())
            .isEqualTo("pushPause.invalid");
    }

    @Test
    void rejectsAnIncompleteOrDuplicatedCategorySet() {
        assertThatThrownBy(() -> service.save(
            request(builder -> builder.categories = List.of(
                new NotificationCategoryPreferenceDTO(NotificationSource.CALENDAR, true, NotificationPushMode.OFF)
            )),
            authentication()
        ))
            .isInstanceOf(RequestAlertException.class)
            .extracting(error -> ((RequestAlertException) error).getErrorKey())
            .isEqualTo("categories.incomplete");

        var duplicated = Arrays.stream(NotificationSource.values())
            .map(source -> new NotificationCategoryPreferenceDTO(NotificationSource.CALENDAR, true, NotificationPushMode.OFF))
            .toList();
        assertThatThrownBy(() -> service.save(request(builder -> builder.categories = duplicated), authentication()))
            .isInstanceOf(RequestAlertException.class)
            .extracting(error -> ((RequestAlertException) error).getErrorKey())
            .isEqualTo("categories.invalid");
    }

    @Test
    void allowsADigestTimeOutsideQuietHours() {
        activeUser();
        when(repository.findByUserKeycloakIdAndDeletedFalse("user-1")).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any(NotificationProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.save(
            request(builder -> {
                builder.quiet = new NotificationQuietHoursDTO(true, LocalTime.of(22, 0), LocalTime.of(7, 0));
                builder.digest = LocalTime.of(9, 0);
                builder.categories = Arrays.stream(NotificationSource.values())
                    .map(source -> new NotificationCategoryPreferenceDTO(source, true, NotificationPushMode.DAILY_DIGEST))
                    .toList();
            }),
            authentication()
        );

        assertThat(result.digestLocalTime()).isEqualTo(LocalTime.of(9, 0));
    }

    @Test
    void refusesAFirstWriteThatCarriesAVersion() {
        activeUser();
        when(repository.findByUserKeycloakIdAndDeletedFalse("user-1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.save(request(builder -> builder.version = 3L), authentication()))
            .isInstanceOf(RequestAlertException.class)
            .extracting(error -> ((RequestAlertException) error).getErrorKey())
            .isEqualTo("preferences.versionConflict");
    }

    @Test
    void refusesAStaleVersionOnAnExistingProfile() {
        activeUser();
        NotificationProfile profile = persistedProfile();
        when(repository.findByUserKeycloakIdAndDeletedFalse("user-1")).thenReturn(Optional.of(profile));

        assertThatThrownBy(() -> service.save(request(builder -> builder.version = 1L), authentication()))
            .isInstanceOf(RequestAlertException.class)
            .extracting(error -> ((RequestAlertException) error).getErrorKey())
            .isEqualTo("preferences.versionConflict");
    }

    @Test
    void reschedulesInheritedRemindersOnlyWhenTheReminderSettingsChange() {
        activeUser();
        NotificationProfile profile = persistedProfile();
        when(repository.findByUserKeycloakIdAndDeletedFalse("user-1")).thenReturn(Optional.of(profile));
        when(repository.saveAndFlush(any(NotificationProfile.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(calendarEventsRepository.findFutureAvailableForUser(any(), any(), any())).thenReturn(List.of());

        // Stessi valori di promemoria: nessuna ripianificazione.
        service.save(request(builder -> builder.version = 0L), authentication());
        verify(calendarEventsRepository, never()).findFutureAvailableForUser(any(), any(), any());

        // Anticipo modificato: la ripianificazione parte nella stessa transazione del salvataggio.
        service.save(request(builder -> {
            builder.version = 0L;
            builder.reminderMinutes = 45;
        }), authentication());
        verify(calendarEventsRepository).findFutureAvailableForUser(any(), any(), any());
    }

    @Test
    void returnsNotFoundWhenTheApiIsDisabledByItsFeatureFlag() {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getNotificationPreferences().setEnabled(false);
        NotificationPreferencesService disabled = new NotificationPreferencesService(
            repository, usersRepository, tenantTimeZoneService, calendarEventsRepository,
            mock(CalendarEventsMapper.class), eventReminderProducer, properties
        );

        assertThatThrownBy(() -> disabled.get(authentication()))
            .isInstanceOf(RequestAlertException.class)
            .extracting(error -> ((RequestAlertException) error).getErrorKey())
            .isEqualTo("preferences.disabled");
    }

    private void activeUser() {
        Users user = new Users();
        user.setKeycloakId("user-1");
        user.setActive(true);
        when(usersRepository.findByKeycloakIdAndDeletedFalse("user-1")).thenReturn(Optional.of(user));
    }

    private static NotificationProfile persistedProfile() {
        Users user = new Users();
        user.setKeycloakId("user-1");
        NotificationProfile profile = new NotificationProfile();
        profile.setUser(user);
        profile.setTimeZone("Europe/Rome");
        profile.setEventRemindersEnabled(true);
        profile.setDefaultCalendarReminderMinutes(30);
        profile.setQuietHoursEnabled(false);
        profile.setQuietStart(LocalTime.of(22, 0));
        profile.setQuietEnd(LocalTime.of(7, 0));
        profile.setDigestLocalTime(LocalTime.of(8, 0));
        profile.setPushPreview(NotificationPushPreview.PRIVATE);
        return profile;
    }

    /** Richiesta valida di base, che ogni test modifica soltanto nel campo in esame. */
    private static final class RequestBuilder {
        private Long version;
        private String timeZone = "Europe/Rome";
        private boolean remindersEnabled = true;
        private int reminderMinutes = 30;
        private NotificationQuietHoursDTO quiet = new NotificationQuietHoursDTO(false, LocalTime.of(22, 0), LocalTime.of(7, 0));
        private ZonedDateTime pausedUntil;
        private LocalTime digest = LocalTime.of(8, 0);
        private NotificationPushPreview preview = NotificationPushPreview.PRIVATE;
        private List<NotificationCategoryPreferenceDTO> categories = Arrays.stream(NotificationSource.values())
            .map(source -> new NotificationCategoryPreferenceDTO(source, true, NotificationPushMode.OFF))
            .toList();
    }

    private static NotificationPreferencesDTO request(java.util.function.Consumer<RequestBuilder> customizer) {
        RequestBuilder builder = new RequestBuilder();
        customizer.accept(builder);
        return new NotificationPreferencesDTO(
            builder.version,
            builder.timeZone,
            builder.remindersEnabled,
            builder.reminderMinutes,
            builder.quiet,
            builder.pausedUntil,
            builder.digest,
            builder.preview,
            builder.categories
        );
    }
}
