package com.fundaro.zodiac.taurus.repository.projection;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface FinanceUnreconciledProjection {
    String getCurrency();

    long getMovementCount();

    BigDecimal getTotalAmount();

    LocalDate getOldestBookingDate();
}
