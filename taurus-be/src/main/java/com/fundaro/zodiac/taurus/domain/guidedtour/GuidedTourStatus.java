package com.fundaro.zodiac.taurus.domain.guidedtour;

/**
 * Where the guided tour of a role stands. A role without a progress row has never started the tour.
 */
public enum GuidedTourStatus {
    IN_PROGRESS,
    COMPLETED,
    SKIPPED
}
