package com.fundaro.zodiac.taurus.service.dto.notification;

import com.fundaro.zodiac.taurus.domain.notification.NotificationSource;
import com.fundaro.zodiac.taurus.domain.notification.NotificationStatus;
import java.time.ZonedDateTime;

public record NotificationDeliveryAdminDTO(
    long id,
    NotificationSource source,
    String operation,
    NotificationStatus status,
    ZonedDateTime occurredAt,
    int attempts,
    ZonedDateTime updatedAt,
    ZonedDateTime nextAttemptAt,
    String errorClass,
    String eventKeyHash
) {}
