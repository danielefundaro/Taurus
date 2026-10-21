package com.fundaro.zodiac.taurus.domain.signature;

public enum SignatureActStatus {
    /**
     * Issue only: waiting for the data that the holder enters instead of filling in the paper form.
     */
    AWAITING_DATA,
    AWAITING_SIGNATURES,
    SIGNED,
    SIGNED_ON_PAPER,
    CANCELLED
}
