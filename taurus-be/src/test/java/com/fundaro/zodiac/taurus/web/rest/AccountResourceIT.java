package com.fundaro.zodiac.taurus.web.rest;

import static com.fundaro.zodiac.taurus.test.util.OAuth2TestUtil.TEST_USER_LOGIN;
import static com.fundaro.zodiac.taurus.test.util.OAuth2TestUtil.authenticationToken;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fundaro.zodiac.taurus.IntegrationTest;
import com.fundaro.zodiac.taurus.security.AuthoritiesConstants;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the {@link AccountResource} REST controller.
 */
@AutoConfigureMockMvc
@IntegrationTest
class AccountResourceIT {

    private Map<String, Object> claims;

    @Autowired
    private MockMvc restMockMvc;

    @BeforeEach
    public void setup() {
        claims = new HashMap<>();
        claims.put("groups", Collections.singletonList(AuthoritiesConstants.ADMIN));
        claims.put("sub", "jane");
        claims.put("email", "jane.doe@jhipster.com");
    }

    @Test
    void testGetExistingAccount() throws Exception {
        restMockMvc
            .perform(
                get("/api/account")
                    .with(csrf())
                    .with(authentication(authenticationToken(claims)))
                    .accept(MediaType.APPLICATION_JSON)
            )
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.login").value("jane"))
            .andExpect(jsonPath("$.authorities").value(AuthoritiesConstants.ADMIN));
    }

    @Test
    void testGetUnknownAccount() throws Exception {
        restMockMvc.perform(get("/api/account").accept(MediaType.APPLICATION_JSON)).andExpect(status().isUnauthorized());
    }

    @Test
    @WithUnauthenticatedMockUser
    void testNonAuthenticatedUser() throws Exception {
        restMockMvc
            .perform(get("/api/authenticate").accept(MediaType.TEXT_PLAIN))
            .andExpect(status().isOk())
            .andExpect(content().string(""));
    }

    @Test
    @WithMockUser(TEST_USER_LOGIN)
    void testAuthenticatedUser() throws Exception {
        restMockMvc
            .perform(get("/api/authenticate").accept(MediaType.TEXT_PLAIN))
            .andExpect(status().isOk())
            .andExpect(content().string(TEST_USER_LOGIN));
    }
}
