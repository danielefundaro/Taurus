package com.fundaro.zodiac.taurus.repository.projection;

import com.fundaro.zodiac.taurus.domain.enumeration.StateEnum;
import java.util.Date;

public interface CalendarResponseProjection {
    Long getEventId();

    String getEventName();

    Date getStartDate();

    StateEnum getState();

    long getResponseCount();
}
