package com.fundaro.zodiac.taurus.web.rest;

import static com.fundaro.zodiac.taurus.test.util.OAuth2TestUtil.authenticationToken;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ReactiveClientRegistrationRepository;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integration tests for the {@link LogoutResource} REST controller.
 */
@AutoConfigureMockMvc
@IntegrationTest
class LogoutResourceIT {

    @Autowired
    private ReactiveClientRegistrationRepository registrations;

    @Autowired
    private ClientRegistration clientRegistration;

    @Autowired
    private MockMvc restMockMvc;

    private Map<String, Object> claims;

    @BeforeEach
    public void before() {
        claims = new HashMap<>();
        claims.put("groups", Collections.singletonList(AuthoritiesConstants.USER));
        claims.put("sub", 123);
    }

    @Test
    void getLogoutInformation() throws Exception {
        final String ORIGIN_URL = "http://localhost:8080";
        String logoutUrl =
            this.registrations.findByRegistrationId("oidc")
                .map(oidc -> oidc.getProviderDetails().getConfigurationMetadata().get("end_session_endpoint").toString())
                .block();
        logoutUrl = logoutUrl + "?id_token_hint=" + com.fundaro.zodiac.taurus.test.util.OAuth2TestUtil.ID_TOKEN + "&post_logout_redirect_uri=" + ORIGIN_URL;

        restMockMvc
            .perform(
                post("http://localhost:8080/api/logout")
                    .with(csrf())
                    .with(authentication(authenticationToken(claims)))
                    .header(HttpHeaders.ORIGIN, ORIGIN_URL)
            )
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(jsonPath("$.logoutUrl").value(logoutUrl));
    }
}
