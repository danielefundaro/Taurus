package com.fundaro.zodiac.taurus.web.rest.errors;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/exception-translator-test")
public class ExceptionTranslatorTestController {

    @GetMapping("/concurrency-failure")
    public void concurrencyFailure() {
        throw new ConcurrencyFailureException("test concurrency failure");
    }

    @PostMapping("/method-argument")
    public void methodArgument(@Valid @RequestBody TestDTO testDTO) {
        // empty method
    }

    @GetMapping("/type-mismatch/{id}")
    public void typeMismatch(@PathVariable Long id) {
        // empty method
    }

    @GetMapping("/missing-servlet-request-part")
    public void missingServletRequestPartException(@RequestPart("part") String part) {
        // empty method
    }

    @GetMapping("/missing-servlet-request-parameter")
    public void missingServletRequestParameterException(@RequestParam("param") String param) {
        // empty method
    }

    @GetMapping("/access-denied")
    public void accessdenied() {
        throw new AccessDeniedException("test access denied!");
    }

    @GetMapping("/unauthorized")
    public void unauthorized() {
        throw new BadCredentialsException("test authentication failed!");
    }

    @GetMapping("/response-status")
    public void exceptionWithResponseStatus() {
        throw new TestResponseStatusException();
    }

    @GetMapping("/request-alert")
    public void requestAlert() {
        throw new RequestAlertException(HttpStatus.UNPROCESSABLE_ENTITY, "Internal description", "testEntity", "test.invalid")
            .with("minimum", LocalDate.of(2026, 11, 10))
            .with("maximum", 3);
    }

    @GetMapping("/internal-server-error")
    public void internalServerError() {
        throw new RuntimeException("Internal failure");
    }

    public static class TestDTO {

        @NotNull(message = "must not be null")
        private String test;

        @Size(max = 5)
        private String code;

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getTest() {
            return test;
        }

        public void setTest(String test) {
            this.test = test;
        }
    }

    @ResponseStatus(value = HttpStatus.BAD_REQUEST, reason = "test response status")
    @SuppressWarnings("serial")
    public static class TestResponseStatusException extends RuntimeException {
    }
}
