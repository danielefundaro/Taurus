package com.fundaro.zodiac.taurus.repository.projection;

import java.math.BigDecimal;
import java.util.Date;

public interface PayoutSettledEventProjection {
    Long getEventId();

    String getEventName();

    Date getStartDate();

    BigDecimal getSettledAmount();
}
