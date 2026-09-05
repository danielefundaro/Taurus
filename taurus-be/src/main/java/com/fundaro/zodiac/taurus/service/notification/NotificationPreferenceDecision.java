package com.fundaro.zodiac.taurus.service.notification;

import com.fundaro.zodiac.taurus.domain.notification.NotificationPushMode;
import com.fundaro.zodiac.taurus.domain.notification.NotificationPushPreview;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public record NotificationPreferenceDecision(
    String userId,
    boolean inAppEnabled,
    NotificationPushMode pushMode,
    boolean eventRemindersEnabled,
    ZoneId timeZone,
    LocalTime digestLocalTime,
    boolean quietHoursEnabled,
    LocalTime quietStart,
    LocalTime quietEnd,
    ZonedDateTime pushPausedUntil,
    NotificationPushPreview pushPreview,
    /** Vero quando una politica {@code REQUIRED} ha riabilitato il canale in-app. */
    boolean requiredOverride
) {}
