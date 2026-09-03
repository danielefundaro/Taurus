package com.fundaro.zodiac.taurus.service.notification;

import com.fundaro.zodiac.taurus.domain.notification.NotificationSeverity;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSource;

public record NotificationDelivery(
    String userId,
    String eventKey,
    String title,
    String message,
    NotificationSource source,
    NotificationSeverity severity,
    String targetPath,
    String actorId
) {}
