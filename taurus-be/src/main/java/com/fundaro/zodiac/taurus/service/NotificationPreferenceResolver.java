package com.fundaro.zodiac.taurus.service;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.notification.NotificationCategoryPreference;
import com.fundaro.zodiac.taurus.domain.notification.NotificationPreferencePolicy;
import com.fundaro.zodiac.taurus.domain.notification.NotificationProfile;
import com.fundaro.zodiac.taurus.domain.notification.NotificationPushMode;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSource;
import com.fundaro.zodiac.taurus.repository.notification.NotificationProfileRepository;
import com.fundaro.zodiac.taurus.service.notification.NotificationPreferenceDecision;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationPreferenceResolver {

    private final NotificationProfileRepository repository;
    private final TenantTimeZoneService tenantTimeZoneService;
    private final ZoneId applicationDefaultZone;

    public NotificationPreferenceResolver(
        NotificationProfileRepository repository,
        TenantTimeZoneService tenantTimeZoneService,
        ApplicationProperties applicationProperties
    ) {
        this.repository = repository;
        this.tenantTimeZoneService = tenantTimeZoneService;
        this.applicationDefaultZone = ZoneId.of(applicationProperties.getNotificationPreferences().getDefaultTimeZone());
    }

    @Transactional(readOnly = true)
    public Map<String, NotificationPreferenceDecision> resolve(
        NotificationSource source,
        NotificationPreferencePolicy policy,
        Collection<String> userIds
    ) {
        Map<String, NotificationPreferenceDecision> decisions = new LinkedHashMap<>();
        ZoneId defaultZone = currentTenantZoneOrDefault();
        userIds.forEach(userId -> decisions.put(userId, defaults(userId, defaultZone)));
        for (NotificationProfile profile : repository.findAllByUserKeycloakIdInAndDeletedFalse(userIds)) {
            String userId = profile.getUser().getKeycloakId();
            NotificationCategoryPreference category = profile.getCategories().stream()
                .filter(value -> value.getSource() == source)
                .findFirst()
                .orElse(null);
            if (category != null) {
                boolean requiredOverride = policy == NotificationPreferencePolicy.REQUIRED && !category.isInAppEnabled();
                boolean inApp = requiredOverride || category.isInAppEnabled();
                decisions.put(userId, new NotificationPreferenceDecision(
                    userId,
                    inApp,
                    category.getPushMode(),
                    profile.isEventRemindersEnabled(),
                    ZoneId.of(profile.getTimeZone()),
                    profile.getDigestLocalTime(),
                    profile.isQuietHoursEnabled(),
                    profile.getQuietStart(),
                    profile.getQuietEnd(),
                    profile.getPushPausedUntil(),
                    profile.getPushPreview(),
                    requiredOverride
                ));
            }
        }
        return decisions;
    }

    private ZoneId currentTenantZoneOrDefault() {
        try {
            return tenantTimeZoneService.currentZoneId();
        } catch (RequestAlertException exception) {
            return applicationDefaultZone;
        }
    }

    private static NotificationPreferenceDecision defaults(String userId, ZoneId timeZone) {
        return new NotificationPreferenceDecision(
            userId,
            true,
            NotificationPushMode.OFF,
            true,
            timeZone,
            LocalTime.of(8, 0),
            false,
            LocalTime.of(22, 0),
            LocalTime.of(7, 0),
            null,
            com.fundaro.zodiac.taurus.domain.notification.NotificationPushPreview.PRIVATE,
            false
        );
    }
}
