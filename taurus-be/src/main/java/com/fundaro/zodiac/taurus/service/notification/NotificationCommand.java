package com.fundaro.zodiac.taurus.service.notification;

import com.fundaro.zodiac.taurus.domain.notification.NotificationSeverity;
import com.fundaro.zodiac.taurus.domain.notification.NotificationSource;
import com.fundaro.zodiac.taurus.domain.notification.NotificationPreferencePolicy;
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
    NotificationPreferencePolicy preferencePolicy,
    String targetPath,
    String actorId,
    String actorDisplayName,
    Set<NotificationAudience> audiences,
    String targetTenantCode
) {
    public NotificationCommand(
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
    ) {
        this(eventKey, source, aggregateType, aggregateId, operation, title, message, severity,
            NotificationPreferencePolicy.CONFIGURABLE, targetPath, actorId, actorDisplayName, audiences, targetTenantCode);
    }
}
