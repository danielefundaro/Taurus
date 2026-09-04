package com.fundaro.zodiac.taurus.repository.projection;

import java.util.Date;

public interface CalendarAttentionProjection {
    long getEventCount();

    Date getEarliestStartDate();
}
