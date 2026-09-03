package com.fundaro.zodiac.taurus.service.notification;

import com.fundaro.zodiac.taurus.domain.notification.NotificationSeverity;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSource;
import java.util.Set;

public record NotificationCommand(
    String eventKey,
    NotificationSource source,
    String aggregateType,
    String aggregateId,
    String operation,
    String title,
    String message,
    NotificationSeverity severity,
    String targetPath,
    String actorId,
    String actorDisplayName,
    Set<NotificationAudience> audiences,
    String targetTenantCode
) {}
