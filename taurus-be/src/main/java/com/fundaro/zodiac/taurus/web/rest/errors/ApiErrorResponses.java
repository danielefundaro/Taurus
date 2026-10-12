package com.fundaro.zodiac.taurus.web.rest.errors;

import jakarta.validation.ConstraintViolation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.security.SecureRandom;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.springframework.core.annotation.AnnotatedElementUtils.findMergedAnnotation;

/**
 * Builds the error response of every failed request: status, key, parameters and error identifier, nothing else.
 * Exception messages, entity names and causes go only to the log line, which carries the same identifier.
 */
public final class ApiErrorResponses {

    private static final Logger LOG = LoggerFactory.getLogger(ApiErrorResponses.class);

    private static final String ERROR_ID_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";
    private static final int ERROR_ID_LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Set<String> CONSTRAINT_ARGUMENTS = Set.of("min", "max", "value");

    private ApiErrorResponses() {
    }

    /**
     * Builds the response body for an exception and logs it with the same error identifier. The body is a plain map
     * in the Problem Details shape rather than a {@code ProblemDetail}, which Spring would complete with the request
     * path as {@code instance}: the response carries exactly the fields of the contract.
     */
    public static Map<String, Object> build(Throwable error, int status) {
        String key = key(error, status);
        String errorId = errorId();
        HttpStatus httpStatus = HttpStatus.resolve(status);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "about:blank");
        body.put("title", httpStatus == null ? "Error" : httpStatus.getReasonPhrase());
        body.put("status", status);
        body.put("message", ErrorConstants.MESSAGE_PREFIX + key);
        if (error instanceof RequestAlertException alert && !alert.getArgs().isEmpty()) {
            body.put("args", alert.getArgs().values());
        }
        if (error instanceof MethodArgumentNotValidException invalid) {
            body.put("fieldErrors", fieldErrors(invalid));
        }
        body.put("errorId", errorId);
        log(error, status, key, errorId);
        return body;
    }

    public static int status(Throwable error) {
        if (error instanceof ErrorResponse response) return response.getStatusCode().value();
        if (error instanceof AccessDeniedException) return HttpStatus.FORBIDDEN.value();
        if (error instanceof AuthenticationException) return HttpStatus.UNAUTHORIZED.value();
        if (isConcurrencyFailure(error)) return HttpStatus.CONFLICT.value();
        ResponseStatus responseStatus = responseStatus(error);
        return responseStatus == null ? HttpStatus.INTERNAL_SERVER_ERROR.value() : responseStatus.code().value();
    }

    static String key(Throwable error, int status) {
        if (error instanceof RequestAlertException alert) return alert.getErrorKey();
        if (error instanceof MethodArgumentNotValidException) return ErrorConstants.VALIDATION;
        if (isConcurrencyFailure(error)) return ErrorConstants.CONCURRENCY_FAILURE;
        if (error instanceof AccessDeniedException) return ErrorConstants.FORBIDDEN;
        if (error instanceof AuthenticationException) return ErrorConstants.UNAUTHENTICATED;
        return ErrorConstants.HTTP_PREFIX + status;
    }

    static String errorId() {
        StringBuilder value = new StringBuilder(ERROR_ID_LENGTH);
        for (int index = 0; index < ERROR_ID_LENGTH; index++) {
            value.append(ERROR_ID_ALPHABET.charAt(RANDOM.nextInt(ERROR_ID_ALPHABET.length())));
        }
        return value.toString();
    }

    private static boolean isConcurrencyFailure(Throwable error) {
        return error instanceof ConcurrencyFailureException || error.getCause() instanceof ConcurrencyFailureException;
    }

    private static ResponseStatus responseStatus(Throwable error) {
        ResponseStatus candidate = findMergedAnnotation(error.getClass(), ResponseStatus.class);
        return candidate == null && error.getCause() != null && error.getCause() != error ? responseStatus(error.getCause()) : candidate;
    }

    private static List<Map<String, Object>> fieldErrors(MethodArgumentNotValidException invalid) {
        return invalid.getBindingResult().getFieldErrors().stream().map(ApiErrorResponses::fieldError).toList();
    }

    private static Map<String, Object> fieldError(FieldError error) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("field", error.getField());
        value.put("code", error.getCode());
        ErrorArgs args = new ErrorArgs();
        if (error.contains(ConstraintViolation.class)) {
            ConstraintViolation<?> violation = error.unwrap(ConstraintViolation.class);
            Map<String, Object> attributes = violation.getConstraintDescriptor().getAttributes();
            attributes.forEach((name, attribute) -> {
                if (CONSTRAINT_ARGUMENTS.contains(name) && (attribute instanceof Integer || attribute instanceof Long)) {
                    args.with(name, attribute);
                }
            });
        }
        if (!args.isEmpty()) value.put("args", args.values());
        return value;
    }

    private static void log(Throwable error, int status, String key, String errorId) {
        String detail = error instanceof RequestAlertException alert && alert.getEntityName() != null
            ? alert.getEntityName() + ": " + error.getMessage()
            : error.getMessage();
        if (status >= 500) {
            LOG.error("error_response errorId={} status={} key={} detail={}", errorId, status, key, detail, error);
        } else {
            LOG.warn("error_response errorId={} status={} key={} detail={}", errorId, status, key, detail);
        }
    }
}
