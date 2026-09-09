package com.fundaro.zodiac.taurus.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fundaro.zodiac.taurus.security.AuthoritiesConstants;
import com.fundaro.zodiac.taurus.service.TenantFeatureService;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.jhipster.config.JHipsterProperties;

@WebMvcTest(controllers = SecurityConfigurationTest.CancelAvailabilityController.class)
@Import({ SecurityConfiguration.class, SecurityConfigurationTest.CancelAvailabilityController.class })
@EnableConfigurationProperties(JHipsterProperties.class)
@TestPropertySource(properties = "spring.security.oauth2.client.provider.oidc.issuer-uri=https://issuer.example")
@SuppressWarnings("removal")
class SecurityConfigurationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private TenantFeatureService tenantFeatureService;

    @ParameterizedTest
    @MethodSource("allowedCancellationRequests")
    void allowsAvailabilityCancellationForTheMatchingRole(String authority, String path) throws Exception {
        mockMvc
            .perform(patch(path).with(user("member").authorities(new SimpleGrantedAuthority(authority))))
            .andExpect(status().isOk());
    }

    @Test
    void deniesAvailabilityCancellationForAnotherRole() throws Exception {
        mockMvc
            .perform(
                patch("/api/external/calendar-events/42/availability/cancel")
                    .with(user("member").authorities(new SimpleGrantedAuthority(AuthoritiesConstants.USER)))
            )
            .andExpect(status().isForbidden());
    }

    private static Stream<Arguments> allowedCancellationRequests() {
        return Stream.of(
            Arguments.of(AuthoritiesConstants.ADMIN, "/api/calendar-events/42/availability/cancel"),
            Arguments.of(AuthoritiesConstants.ADMIN, "/api/calendar-events/series/7/availability/cancel"),
            Arguments.of(AuthoritiesConstants.USER, "/api/user/calendar-events/42/availability/cancel"),
            Arguments.of(AuthoritiesConstants.USER, "/api/user/calendar-events/series/7/availability/cancel"),
            Arguments.of(AuthoritiesConstants.USER_EXTERNAL, "/api/external/calendar-events/42/availability/cancel"),
            Arguments.of(AuthoritiesConstants.USER_EXTERNAL, "/api/external/calendar-events/series/7/availability/cancel")
        );
    }

    @RestController
    static class CancelAvailabilityController {

        @PatchMapping({
            "/api/calendar-events/{id}/availability/cancel",
            "/api/calendar-events/series/{id}/availability/cancel",
            "/api/user/calendar-events/{id}/availability/cancel",
            "/api/user/calendar-events/series/{id}/availability/cancel",
            "/api/external/calendar-events/{id}/availability/cancel",
            "/api/external/calendar-events/series/{id}/availability/cancel",
        })
        void cancelAvailability() {}
    }
}
