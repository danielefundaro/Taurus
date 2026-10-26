package com.fundaro.zodiac.taurus.config;

import com.fundaro.zodiac.taurus.multitenancy.TenantProvisioningStatus;
import com.fundaro.zodiac.taurus.security.AuthoritiesConstants;
import com.fundaro.zodiac.taurus.service.TenantFeatureService;
import com.fundaro.zodiac.taurus.web.rest.errors.SecurityProblemSupport;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.jhipster.config.JHipsterProperties;

import java.util.stream.Stream;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La serie ricorrente è amministrativa: solo amministratore e super amministratore la creano, la
 * modificano e la cancellano, anteprima compresa.
 *
 * <p>La disponibilità massiva di una serie invece appartiene a chi risponde: ogni ruolo passa dal
 * proprio percorso e non da quello di un altro. È la parte autorizzativa chiesta dalla specifica
 * delle serie ricorrenti, verificata sulle regole vere di {@link SecurityConfiguration}.
 */
@WebMvcTest(controllers = CalendarSeriesSecurityConfigurationTest.CalendarSeriesController.class)
@Import({SecurityConfiguration.class, SecurityProblemSupport.class, CalendarSeriesSecurityConfigurationTest.CalendarSeriesController.class})
@EnableConfigurationProperties(JHipsterProperties.class)
@TestPropertySource(properties = "spring.security.oauth2.client.provider.oidc.issuer-uri=https://issuer.example")
@SuppressWarnings("removal")
class CalendarSeriesSecurityConfigurationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private TenantFeatureService tenantFeatureService;

    @MockBean
    private TenantProvisioningStatus tenantProvisioningStatus;

    @ParameterizedTest(name = "{0} {1} {2} -> {3}")
    @MethodSource("requests")
    void appliesTheSeriesAccessRules(String authority, HttpMethod method, String path, int expectedStatus) throws Exception {
        mockMvc
            .perform(request(method, path).with(user("someone").authorities(new SimpleGrantedAuthority(authority))))
            .andExpect(status().is(expectedStatus));
    }

    private static Stream<Arguments> requests() {
        return Stream.of(
            Arguments.of(AuthoritiesConstants.ADMIN, HttpMethod.POST, "/api/calendar-event-series", 200),
            Arguments.of(AuthoritiesConstants.ADMIN, HttpMethod.POST, "/api/calendar-event-series/preview", 200),
            Arguments.of(AuthoritiesConstants.ADMIN, HttpMethod.GET, "/api/calendar-event-series/7", 200),
            Arguments.of(AuthoritiesConstants.ADMIN, HttpMethod.PATCH, "/api/calendar-event-series/7", 200),
            Arguments.of(AuthoritiesConstants.ADMIN, HttpMethod.DELETE, "/api/calendar-event-series/7", 200),
            Arguments.of(AuthoritiesConstants.SUPER_ADMIN, HttpMethod.POST, "/api/calendar-event-series", 200),
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.POST, "/api/calendar-event-series", 403),
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.POST, "/api/calendar-event-series/preview", 403),
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.GET, "/api/calendar-event-series/7", 403),
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.PATCH, "/api/calendar-event-series/7", 403),
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.DELETE, "/api/calendar-event-series/7", 403),
            Arguments.of(AuthoritiesConstants.USER_EXTERNAL, HttpMethod.POST, "/api/calendar-event-series", 403),
            Arguments.of(AuthoritiesConstants.ARCHIVIST, HttpMethod.POST, "/api/calendar-event-series", 403),
            Arguments.of(AuthoritiesConstants.TREASURER, HttpMethod.POST, "/api/calendar-event-series", 403),
            // La disponibilità massiva: ogni ruolo dal proprio percorso.
            Arguments.of(AuthoritiesConstants.ADMIN, HttpMethod.PATCH, "/api/calendar-events/series/7/availability", 200),
            Arguments.of(AuthoritiesConstants.ADMIN, HttpMethod.PATCH, "/api/calendar-events/series/7/availability/cancel", 200),
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.PATCH, "/api/user/calendar-events/series/7/availability", 200),
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.PATCH, "/api/user/calendar-events/series/7/availability/cancel", 200),
            Arguments.of(AuthoritiesConstants.USER_EXTERNAL, HttpMethod.PATCH, "/api/external/calendar-events/series/7/availability", 200),
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.PATCH, "/api/calendar-events/series/7/availability", 403),
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.PATCH, "/api/external/calendar-events/series/7/availability", 403),
            Arguments.of(AuthoritiesConstants.USER_EXTERNAL, HttpMethod.PATCH, "/api/user/calendar-events/series/7/availability", 403),
            Arguments.of(AuthoritiesConstants.ADMIN, HttpMethod.PATCH, "/api/user/calendar-events/series/7/availability", 403)
        );
    }

    @RestController
    static class CalendarSeriesController {

        @PostMapping({"/api/calendar-event-series", "/api/calendar-event-series/preview"})
        void post() {
        }

        @GetMapping("/api/calendar-event-series/{id}")
        void get() {
        }

        @PatchMapping({
            "/api/calendar-event-series/{id}",
            "/api/calendar-events/series/{seriesId}/availability",
            "/api/calendar-events/series/{seriesId}/availability/cancel",
            "/api/user/calendar-events/series/{seriesId}/availability",
            "/api/user/calendar-events/series/{seriesId}/availability/cancel",
            "/api/external/calendar-events/series/{seriesId}/availability"
        })
        void patch() {
        }

        @DeleteMapping("/api/calendar-event-series/{id}")
        void delete() {
        }
    }
}
