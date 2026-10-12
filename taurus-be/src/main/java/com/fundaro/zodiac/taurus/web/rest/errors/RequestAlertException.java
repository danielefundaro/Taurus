package com.fundaro.zodiac.taurus.web.rest.errors;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * An application error. The client receives only the status, the key and the {@link ErrorArgs parameters}: the
 * description and the entity name are written to the log and never serialized, so they may contain technical details.
 * The frontend builds the message shown to the user from the key.
 */
@SuppressWarnings("java:S110") // Inheritance tree of classes should not be too deep
public class RequestAlertException extends ErrorResponseException {

    private final String entityName;

    private final String errorKey;

    private final ErrorArgs args = new ErrorArgs();

    public RequestAlertException(HttpStatus status, String logMessage, String entityName, String errorKey) {
        super(status, problem(status, logMessage), null);
        this.entityName = entityName;
        this.errorKey = errorKey;
    }

    private static ProblemDetail problem(HttpStatus status, String logMessage) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle(logMessage);
        return problem;
    }

    /**
     * Adds a parameter of the error, as listed for its key in the error catalog.
     */
    public RequestAlertException with(String name, Object value) {
        args.with(name, value);
        return this;
    }

    /**
     * Adds a whole set of parameters, for example those of a rule violation.
     */
    public RequestAlertException with(ErrorArgs other) {
        args.withAll(other);
        return this;
    }

    public String getEntityName() {
        return entityName;
    }

    public String getErrorKey() {
        return errorKey;
    }

    public ErrorArgs getArgs() {
        return args;
    }
}
