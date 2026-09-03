package com.fundaro.zodiac.taurus.service.notification;

import com.fundaro.zodiac.taurus.domain.notification.NotificationSource;
import java.time.ZonedDateTime;

public interface NotificationPendingSummary {
    NotificationSource getSource();
    long getPendingCount();
    ZonedDateTime getOldestOccurredAt();
}
