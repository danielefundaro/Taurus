package com.fundaro.zodiac.taurus.repository.projection;

import java.time.LocalDate;

public interface InventoryExpirationProjection {
    long getAssignmentCount();

    LocalDate getEarliestExpirationDate();

    long getOverdueCount();
}
