package com.fundaro.zodiac.taurus.web.rest.errors;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Translates every server side exception into the error response of docs/application-errors-standard.md: Problem
 * Details with status, key, parameters and error identifier only. The details stay in the log.
 */
@ControllerAdvice
@Component("jhiExceptionTranslator")
public class ExceptionTranslator extends ResponseEntityExceptionHandler {

    @ExceptionHandler
    public ResponseEntity<Object> handleAnyException(Throwable ex, WebRequest request) {
        return respond(ex, ApiErrorResponses.status(ex), new HttpHeaders());
    }

    // Spring MVC exceptions (validation, type mismatch, unreadable body, unsupported method…) arrive here.
    @Nullable
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
        Exception ex,
        @Nullable Object body,
        HttpHeaders headers,
        HttpStatusCode statusCode,
        WebRequest request
    ) {
        return respond(ex, statusCode.value(), headers);
    }

    private static ResponseEntity<Object> respond(Throwable ex, int status, HttpHeaders headers) {
        HttpHeaders responseHeaders = new HttpHeaders();
        if (headers != null) responseHeaders.putAll(headers);
        responseHeaders.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        return new ResponseEntity<>(ApiErrorResponses.build(ex, status), responseHeaders, HttpStatusCode.valueOf(status));
    }
}
