package com.fundaro.zodiac.taurus.web.rest.errors;

import org.springframework.http.HttpStatus;
import org.springframework.web.ErrorResponseException;
import tech.jhipster.web.rest.errors.ProblemDetailWithCause;
import tech.jhipster.web.rest.errors.ProblemDetailWithCause.ProblemDetailWithCauseBuilder;

import java.net.URI;

@SuppressWarnings("java:S110") // Inheritance tree of classes should not be too deep
public class RequestAlertException extends ErrorResponseException {

    private final String entityName;

    private final String errorKey;

    public RequestAlertException(HttpStatus status, String defaultMessage, String entityName, String errorKey) {
        this(ErrorConstants.DEFAULT_TYPE, status, defaultMessage, entityName, errorKey);
    }

    public RequestAlertException(URI type, HttpStatus status, String defaultMessage, String entityName, String errorKey) {
        super(
            status,
            ProblemDetailWithCauseBuilder.instance()
                .withStatus(status.value())
                .withType(type)
                .withTitle(defaultMessage)
                .withProperty("message", "error." + errorKey)
                .withProperty("params", entityName)
                .build(),
            null
        );
        this.entityName = entityName;
        this.errorKey = errorKey;
    }

    public String getEntityName() {
        return entityName;
    }

    public String getErrorKey() {
        return errorKey;
    }

    public ProblemDetailWithCause getProblemDetailWithCause() {
        return (ProblemDetailWithCause) this.getBody();
    }
}
