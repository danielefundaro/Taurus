package com.fundaro.zodiac.taurus.web.rest.errors;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fundaro.zodiac.taurus.IntegrationTest;
import org.hamcrest.core.AnyOf;
import org.hamcrest.core.IsEqual;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests {@link ExceptionTranslator} controller advice.
 */
@WithMockUser
@AutoConfigureMockMvc
@IntegrationTest
class ExceptionTranslatorIT {

    @Autowired
    private MockMvc restMockMvc;

    @Test
    void testConcurrencyFailure() throws Exception {
        restMockMvc
            .perform(get("/api/exception-translator-test/concurrency-failure").with(csrf()))
            .andExpect(status().isEqualTo(HttpStatus.CONFLICT.value()))
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.message").value(ErrorConstants.ERR_CONCURRENCY_FAILURE));
    }

    @Test
    void testMethodArgumentNotValid() throws Exception {
        restMockMvc
            .perform(
                post("/api/exception-translator-test/method-argument")
                    .with(csrf())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
            )
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.message").value(ErrorConstants.ERR_VALIDATION))
            .andExpect(jsonPath("$.fieldErrors.[0].objectName").value("test"))
            .andExpect(jsonPath("$.fieldErrors.[0].field").value("test"))
            .andExpect(jsonPath("$.fieldErrors.[0].message").value("must not be null"));
    }

    @Test
    void testMissingRequestPart() throws Exception {
        restMockMvc
            .perform(get("/api/exception-translator-test/missing-servlet-request-part").with(csrf()))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.message").value("error.http.400"));
    }

    @Test
    void testMissingRequestParameter() throws Exception {
        restMockMvc
            .perform(get("/api/exception-translator-test/missing-servlet-request-parameter").with(csrf()))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.message").value("error.http.400"));
    }

    @Test
    void testAccessDenied() throws Exception {
        restMockMvc
            .perform(get("/api/exception-translator-test/access-denied").with(csrf()))
            .andExpect(status().isForbidden())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.message").value("error.http.403"))
            .andExpect(jsonPath("$.detail").value("test access denied!"));
    }

    @Test
    void testUnauthorized() throws Exception {
        restMockMvc
            .perform(get("/api/exception-translator-test/unauthorized").with(csrf()))
            .andExpect(status().isUnauthorized())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.message").value("error.http.401"))
            .andExpect(jsonPath("$.path").value("/api/exception-translator-test/unauthorized"))
            .andExpect(jsonPath("$.detail").value(AnyOf.anyOf(IsEqual.equalTo("test authentication failed!"), IsEqual.equalTo("Invalid credentials"))));
    }

    @Test
    void testMethodNotSupported() throws Exception {
        restMockMvc
            .perform(post("/api/exception-translator-test/access-denied").with(csrf()))
            .andExpect(status().isEqualTo(HttpStatus.METHOD_NOT_ALLOWED.value()))
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.message").value("error.http.405"))
            .andExpect(jsonPath("$.detail").value("405 METHOD_NOT_ALLOWED \"Request method 'POST' is not supported.\""));
    }

    @Test
    void testExceptionWithResponseStatus() throws Exception {
        restMockMvc
            .perform(get("/api/exception-translator-test/response-status").with(csrf()))
            .andExpect(status().isBadRequest())
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.message").value("error.http.400"))
            .andExpect(jsonPath("$.title").value("test response status"));
    }

    @Test
    void testInternalServerError() throws Exception {
        restMockMvc
            .perform(get("/api/exception-translator-test/internal-server-error").with(csrf()))
            .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.message").value("error.http.500"))
            .andExpect(jsonPath("$.title").value("Internal Server Error"));
    }
}
