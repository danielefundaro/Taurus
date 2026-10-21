package com.fundaro.zodiac.taurus.service.signature;

/**
 * Too many requests to the public signature page from the same address or with the same token.
 */
public class SignatureLinkRateLimitException extends RuntimeException {

    public SignatureLinkRateLimitException() {
        super("Too many requests to the signature page", null, false, false);
    }
}
