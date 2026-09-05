package com.fundaro.zodiac.taurus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.Users;
import com.fundaro.zodiac.taurus.domain.notification.NotificationCategoryPreference;
import com.fundaro.zodiac.taurus.domain.notification.NotificationPreferencePolicy;
import com.fundaro.zodiac.taurus.domain.notification.NotificationProfile;
import com.fundaro.zodiac.taurus.domain.notification.NotificationPushMode;
import com.fundaro.zodiac.taurus.domain.notification.NotificationPushPreview;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSource;
import com.fundaro.zodiac.taurus.repository.notification.NotificationProfileRepository;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class NotificationPreferenceResolverTest {

    private final NotificationProfileRepository repository = mock(NotificationProfileRepository.class);
    private final TenantTimeZoneService tenantTimeZoneService = mock(TenantTimeZoneService.class);
    private NotificationPreferenceResolver resolver;

    @BeforeEach
    void setUp() {
        when(tenantTimeZoneService.currentZoneId()).thenReturn(ZoneId.of("Europe/Rome"));
        resolver = new NotificationPreferenceResolver(repository, tenantTimeZoneService, new ApplicationProperties());
    }

    @Test
    void fallsBackToSafeDefaultsWhenNoProfileExists() {
        when(repository.findAllByUserKeycloakIdInAndDeletedFalse(any())).thenReturn(List.of());

        var decisions = resolver.resolve(NotificationSource.CALENDAR, NotificationPreferencePolicy.CONFIGURABLE, Set.of("user-1"));

        var decision = decisions.get("user-1");
        assertThat(decision.inAppEnabled()).isTrue();
        assertThat(decision.pushMode()).isEqualTo(NotificationPushMode.OFF);
        assertThat(decision.eventRemindersEnabled()).isTrue();
        assertThat(decision.quietHoursEnabled()).isFalse();
        assertThat(decision.pushPreview()).isEqualTo(NotificationPushPreview.PRIVATE);
        assertThat(decision.requiredOverride()).isFalse();
        assertThat(decision.timeZone()).isEqualTo(ZoneId.of("Europe/Rome"));
    }

    @Test
    void appliesTheSavedCategoryPreferenceForAConfigurableEvent() {
        when(repository.findAllByUserKeycloakIdInAndDeletedFalse(any()))
            .thenReturn(List.of(profile(NotificationSource.CALENDAR, false, NotificationPushMode.DAILY_DIGEST)));

        var decision = resolver
            .resolve(NotificationSource.CALENDAR, NotificationPreferencePolicy.CONFIGURABLE, Set.of("user-1"))
            .get("user-1");

        assertThat(decision.inAppEnabled()).isFalse();
        assertThat(decision.pushMode()).isEqualTo(NotificationPushMode.DAILY_DIGEST);
        assertThat(decision.requiredOverride()).isFalse();
    }

    @Test
    void keepsARequiredEventInAppWithoutTouchingItsPushMode() {
        when(repository.findAllByUserKeycloakIdInAndDeletedFalse(any()))
            .thenReturn(List.of(profile(NotificationSource.IDENTITY, false, NotificationPushMode.OFF)));

        var decision = resolver
            .resolve(NotificationSource.IDENTITY, NotificationPreferencePolicy.REQUIRED, Set.of("user-1"))
            .get("user-1");

        assertThat(decision.inAppEnabled()).isTrue();
        assertThat(decision.requiredOverride()).isTrue();
        // REQUIRED non forza il push: la modalità scelta dall'utente resta autorevole.
        assertThat(decision.pushMode()).isEqualTo(NotificationPushMode.OFF);
    }

    @Test
    void doesNotFlagAnOverrideWhenTheCategoryIsAlreadyEnabled() {
        when(repository.findAllByUserKeycloakIdInAndDeletedFalse(any()))
            .thenReturn(List.of(profile(NotificationSource.IDENTITY, true, NotificationPushMode.IMMEDIATE)));

        var decision = resolver
            .resolve(NotificationSource.IDENTITY, NotificationPreferencePolicy.REQUIRED, Set.of("user-1"))
            .get("user-1");

        assertThat(decision.inAppEnabled()).isTrue();
        assertThat(decision.requiredOverride()).isFalse();
    }

    @Test
    void usesSafeDefaultsForASourceTheProfileHasNoRowFor() {
        when(repository.findAllByUserKeycloakIdInAndDeletedFalse(any()))
            .thenReturn(List.of(profile(NotificationSource.CALENDAR, false, NotificationPushMode.IMMEDIATE)));

        var decision = resolver
            .resolve(NotificationSource.FINANCE, NotificationPreferencePolicy.CONFIGURABLE, Set.of("user-1"))
            .get("user-1");

        assertThat(decision.inAppEnabled()).isTrue();
        assertThat(decision.pushMode()).isEqualTo(NotificationPushMode.OFF);
    }

    @Test
    void returnsOneDecisionPerRecipientWithASingleBulkQuery() {
        when(repository.findAllByUserKeycloakIdInAndDeletedFalse(any()))
            .thenReturn(List.of(profile(NotificationSource.CALENDAR, false, NotificationPushMode.IMMEDIATE)));

        var decisions = resolver.resolve(
            NotificationSource.CALENDAR,
            NotificationPreferencePolicy.CONFIGURABLE,
            Set.of("user-1", "user-2", "user-3")
        );

        assertThat(decisions).hasSize(3);
        assertThat(decisions.get("user-1").inAppEnabled()).isFalse();
        assertThat(decisions.get("user-2").inAppEnabled()).isTrue();
        org.mockito.Mockito.verify(repository, org.mockito.Mockito.times(1)).findAllByUserKeycloakIdInAndDeletedFalse(any());
    }

    private static NotificationProfile profile(NotificationSource source, boolean inApp, NotificationPushMode pushMode) {
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
        NotificationCategoryPreference category = new NotificationCategoryPreference();
        category.setSource(source);
        category.setInAppEnabled(inApp);
        category.setPushMode(pushMode);
        profile.getCategories().add(category);
        return profile;
    }
}
