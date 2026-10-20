package com.fundaro.zodiac.taurus.domain.signature;

/**
 * How a signature was given, as recorded in its evidence.
 */
public enum SignatureEvidenceMethod {
    EMAIL_OTP,
    TOTP,
    CIESIGN,
    PAPER_DECLARATION
}
