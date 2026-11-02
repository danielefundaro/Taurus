package com.fundaro.zodiac.taurus.domain.instrumentsuggestion;

public enum InstrumentSuggestionStatus {
    /** Waiting for a decision. */
    OPEN,
    /** The instrument created or associated was assigned to the part. */
    ACCEPTED,
    /** Ignored for this part. */
    IGNORED
}
