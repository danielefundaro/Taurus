package com.fundaro.zodiac.taurus.web.rest.errors;

import com.fundaro.zodiac.taurus.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests of the error response: status, key, parameters and error identifier, never technical details.
 */
@WithMockUser
@AutoConfigureMockMvc
@IntegrationTest
class ExceptionTranslatorIT {

    @Autowired
    private MockMvc restMockMvc;

    @Test
    void testConcurrencyFailure() throws Exception {
        expectMinimalProblem(restMockMvc.perform(get("/api/exception-translator-test/concurrency-failure").with(csrf())))
            .andExpect(status().is(HttpStatus.CONFLICT.value()))
            .andExpect(jsonPath("$.message").value("error.concurrencyFailure"));
    }

    @Test
    void testMethodArgumentNotValid() throws Exception {
        expectMinimalProblem(
            restMockMvc.perform(
                post("/api/exception-translator-test/method-argument").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{}")
            )
        )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.validation"))
            .andExpect(jsonPath("$.fieldErrors.[0].field").value("test"))
            .andExpect(jsonPath("$.fieldErrors.[0].code").value("NotNull"))
            .andExpect(jsonPath("$.fieldErrors.[0].objectName").doesNotExist())
            .andExpect(jsonPath("$.fieldErrors.[0].message").doesNotExist());
    }

    @Test
    void sendsTheNumericAttributesOfAFailedConstraint() throws Exception {
        restMockMvc
            .perform(
                post("/api/exception-translator-test/method-argument")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"test\":\"x\",\"code\":\"too-long\"}")
            )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors.[0].field").value("code"))
            .andExpect(jsonPath("$.fieldErrors.[0].code").value("Size"))
            .andExpect(jsonPath("$.fieldErrors.[0].args.max").value(5))
            .andExpect(jsonPath("$.fieldErrors.[0].args.min").value(0));
    }

    @Test
    void testTypeMismatch() throws Exception {
        expectMinimalProblem(restMockMvc.perform(get("/api/exception-translator-test/type-mismatch/undefined").with(csrf())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.http.400"));
    }

    @Test
    void testHttpMessageNotReadable() throws Exception {
        expectMinimalProblem(
            restMockMvc.perform(
                post("/api/exception-translator-test/method-argument").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{")
            )
        )
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.http.400"));
    }

    @Test
    void testMissingRequestPart() throws Exception {
        expectMinimalProblem(restMockMvc.perform(get("/api/exception-translator-test/missing-servlet-request-part").with(csrf())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.http.400"));
    }

    @Test
    void testMissingRequestParameter() throws Exception {
        expectMinimalProblem(restMockMvc.perform(get("/api/exception-translator-test/missing-servlet-request-parameter").with(csrf())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.http.400"));
    }

    @Test
    void testAccessDenied() throws Exception {
        expectMinimalProblem(restMockMvc.perform(get("/api/exception-translator-test/access-denied").with(csrf())))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value("error.auth.forbidden"));
    }

    @Test
    void testUnauthorized() throws Exception {
        expectMinimalProblem(restMockMvc.perform(get("/api/exception-translator-test/unauthorized").with(csrf())))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value("error.auth.unauthenticated"));
    }

    @Test
    @WithAnonymousUser
    void givesTheSecurityChainRejectionsTheSameBody() throws Exception {
        expectMinimalProblem(restMockMvc.perform(get("/api/exception-translator-test/request-alert").with(csrf())))
            .andExpect(status().isUnauthorized())
            .andExpect(header().exists("WWW-Authenticate"))
            .andExpect(jsonPath("$.message").value("error.auth.unauthenticated"));
    }

    @Test
    void testMethodNotSupported() throws Exception {
        expectMinimalProblem(restMockMvc.perform(post("/api/exception-translator-test/access-denied").with(csrf())))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(jsonPath("$.message").value("error.http.405"));
    }

    @Test
    void testExceptionWithResponseStatus() throws Exception {
        expectMinimalProblem(restMockMvc.perform(get("/api/exception-translator-test/response-status").with(csrf())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("error.http.400"))
            .andExpect(jsonPath("$.title").value("Bad Request"));
    }

    @Test
    void sendsTheKeyAndTheParametersOfApplicationErrorsButNotTheirDescription() throws Exception {
        expectMinimalProblem(restMockMvc.perform(get("/api/exception-translator-test/request-alert").with(csrf())))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.type").value("about:blank"))
            .andExpect(jsonPath("$.title").value("Unprocessable Entity"))
            .andExpect(jsonPath("$.message").value("error.test.invalid"))
            .andExpect(jsonPath("$.args.minimum").value("2026-11-10"))
            .andExpect(jsonPath("$.args.maximum").value(3))
            .andExpect(content().string(not(containsString("Internal description"))))
            .andExpect(content().string(not(containsString("testEntity"))));
    }

    @Test
    void testInternalServerError() throws Exception {
        expectMinimalProblem(restMockMvc.perform(get("/api/exception-translator-test/internal-server-error").with(csrf())))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.message").value("error.http.500"))
            .andExpect(jsonPath("$.title").value("Internal Server Error"))
            .andExpect(content().string(not(containsString("Internal failure"))));
    }

    /**
     * Checks the fields every error response has and those it must never have.
     */
    private static ResultActions expectMinimalProblem(ResultActions result) throws Exception {
        return result
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.errorId").value(matchesPattern("[0-9A-Z]{12}")))
            .andExpect(jsonPath("$.detail").doesNotExist())
            .andExpect(jsonPath("$.path").doesNotExist())
            .andExpect(jsonPath("$.params").doesNotExist())
            .andExpect(jsonPath("$.cause").doesNotExist())
            .andExpect(jsonPath("$.instance").doesNotExist());
    }
}
