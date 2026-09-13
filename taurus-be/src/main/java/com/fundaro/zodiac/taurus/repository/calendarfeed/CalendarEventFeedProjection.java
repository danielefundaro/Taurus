package com.fundaro.zodiac.taurus.repository.calendarfeed;

import java.util.Date;
import java.util.UUID;

public interface CalendarEventFeedProjection {
    UUID getUid();

    Integer getSequence();

    Date getModifiedAt();

    Date getStartAt();

    Date getEndAt();

    String getSummary();

    String getLocation();

    String getDescription();

    Long getEventId();
}
