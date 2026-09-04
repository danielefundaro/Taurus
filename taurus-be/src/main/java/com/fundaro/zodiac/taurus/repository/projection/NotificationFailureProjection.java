package com.fundaro.zodiac.taurus.repository.projection;

import java.time.ZonedDateTime;

public interface NotificationFailureProjection {
    long getFailureCount();

    ZonedDateTime getOldestOccurredAt();
}
