package com.fundaro.zodiac.taurus.web.rest.errors;

/**
 * Keys of the errors that do not come from a {@link RequestAlertException}. See docs/application-errors-standard.md.
 */
public final class ErrorConstants {

    public static final String MESSAGE_PREFIX = "error.";
    public static final String VALIDATION = "validation";
    public static final String CONCURRENCY_FAILURE = "concurrencyFailure";
    public static final String UNAUTHENTICATED = "auth.unauthenticated";
    public static final String FORBIDDEN = "auth.forbidden";
    public static final String HTTP_PREFIX = "http.";

    private ErrorConstants() {
    }
}
