package com.fundaro.zodiac.taurus.service.signature;

/**
 * A signature link that cannot be used: unknown, expired or revoked token, inactive tenant or inventory disabled. The
 * public page answers {@code 404} without telling which.
 */
public class SignatureLinkUnavailableException extends RuntimeException {

    public SignatureLinkUnavailableException() {
        super("Signature link not available", null, false, false);
    }
}
