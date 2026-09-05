package com.fundaro.zodiac.taurus.service.dto.notification;

import com.fundaro.zodiac.taurus.domain.notification.NotificationPushMode;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSource;
import jakarta.validation.constraints.NotNull;

public record NotificationCategoryPreferenceDTO(
    @NotNull NotificationSource source,
    boolean inAppEnabled,
    @NotNull NotificationPushMode pushMode
) {}
