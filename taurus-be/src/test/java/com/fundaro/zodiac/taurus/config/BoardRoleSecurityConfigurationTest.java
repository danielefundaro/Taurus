package com.fundaro.zodiac.taurus.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fundaro.zodiac.taurus.multitenancy.TenantProvisioningStatus;
import com.fundaro.zodiac.taurus.security.AuthoritiesConstants;
import com.fundaro.zodiac.taurus.service.TenantFeatureService;
import com.fundaro.zodiac.taurus.web.rest.errors.SecurityProblemSupport;
import java.util.stream.Stream;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.jhipster.config.JHipsterProperties;

/**
 * The board manages the association as the administrator does, except for the tenant configuration and the technical
 * tools (docs/board-role-spec.md). Every path answers 200 when the HTTP rules let the request through.
 */
@WebMvcTest(controllers = BoardRoleSecurityConfigurationTest.AnyPathController.class)
@Import({ SecurityConfiguration.class, SecurityProblemSupport.class, BoardRoleSecurityConfigurationTest.AnyPathController.class })
@EnableConfigurationProperties(JHipsterProperties.class)
@TestPropertySource(properties = "spring.security.oauth2.client.provider.oidc.issuer-uri=https://issuer.example")
@SuppressWarnings("removal")
class BoardRoleSecurityConfigurationTest {

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

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("managedRequests")
    void letsTheBoardAndTheAdministratorManageTheAssociation(HttpMethod method, String path) throws Exception {
        perform(method, path, AuthoritiesConstants.ADMIN, 200);
        perform(method, path, AuthoritiesConstants.BOARD, 200);
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("administratorRequests")
    void keepsTheTenantConfigurationAndTheTechnicalToolsToTheAdministrator(HttpMethod method, String path) throws Exception {
        perform(method, path, AuthoritiesConstants.ADMIN, 200);
        perform(method, path, AuthoritiesConstants.SUPER_ADMIN, 200);
        perform(method, path, AuthoritiesConstants.BOARD, 403);
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("superAdministratorRequests")
    void keepsThePlatformAdministrationToTheSuperAdministrator(HttpMethod method, String path) throws Exception {
        perform(method, path, AuthoritiesConstants.SUPER_ADMIN, 200);
        perform(method, path, AuthoritiesConstants.BOARD, 403);
    }

    private void perform(HttpMethod method, String path, String authority, int expected) throws Exception {
        mockMvc
            .perform(request(method, path).with(user("member").authorities(new SimpleGrantedAuthority(authority))))
            .andExpect(status().is(expected));
    }

    private static Stream<Arguments> managedRequests() {
        return Stream.of(
            Arguments.of(HttpMethod.GET, "/api/dashboard/operations/calendar-events"),
            Arguments.of(HttpMethod.GET, "/api/inventory/items"),
            Arguments.of(HttpMethod.POST, "/api/inventory/assignments/3/loan-sheets"),
            Arguments.of(HttpMethod.POST, "/api/finance/movements"),
            Arguments.of(HttpMethod.GET, "/api/users"),
            Arguments.of(HttpMethod.PUT, "/api/users/5"),
            Arguments.of(HttpMethod.DELETE, "/api/users/5/gdpr"),
            Arguments.of(HttpMethod.POST, "/api/albums"),
            Arguments.of(HttpMethod.PUT, "/api/tracks/4"),
            Arguments.of(HttpMethod.POST, "/api/instruments"),
            Arguments.of(HttpMethod.POST, "/api/media"),
            Arguments.of(HttpMethod.DELETE, "/api/media/8"),
            Arguments.of(HttpMethod.POST, "/api/calendar-events"),
            Arguments.of(HttpMethod.PUT, "/api/calendar-events/42"),
            Arguments.of(HttpMethod.DELETE, "/api/calendar-events/42"),
            Arguments.of(HttpMethod.PATCH, "/api/calendar-events/42/availability"),
            Arguments.of(HttpMethod.GET, "/api/calendar-events/42/preparation"),
            Arguments.of(HttpMethod.PUT, "/api/calendar-events/42/preparation/configuration"),
            Arguments.of(HttpMethod.PUT, "/api/calendar-events/42/preparation/program"),
            Arguments.of(HttpMethod.GET, "/api/calendar-events/42/preparation/catalogue"),
            Arguments.of(HttpMethod.POST, "/api/calendar-events/42/preparation/materials/3/confirm"),
            Arguments.of(HttpMethod.POST, "/api/calendar-events/42/preparation/presence-confirmation"),
            Arguments.of(HttpMethod.POST, "/api/calendar-events/42/preparation/budget-confirmation"),
            Arguments.of(HttpMethod.POST, "/api/calendar-events/42/preparation/no-movements-confirmation"),
            Arguments.of(HttpMethod.POST, "/api/calendar-event-series")
        );
    }

    private static Stream<Arguments> administratorRequests() {
        return Stream.of(
            Arguments.of(HttpMethod.GET, "/api/onboarding/status"),
            Arguments.of(HttpMethod.POST, "/api/onboarding/imports"),
            Arguments.of(HttpMethod.GET, "/api/admin/notification-delivery"),
            Arguments.of(HttpMethod.GET, "/api/admin/calendar-feeds"),
            Arguments.of(HttpMethod.GET, "/v3/api-docs"),
            Arguments.of(HttpMethod.GET, "/management/prometheus"),
            Arguments.of(HttpMethod.GET, "/management/loggers")
        );
    }

    private static Stream<Arguments> superAdministratorRequests() {
        return Stream.of(
            Arguments.of(HttpMethod.GET, "/api/tenants"),
            Arguments.of(HttpMethod.GET, "/api/legal/documents"),
            Arguments.of(HttpMethod.GET, "/api/tenant-features/capabilities")
        );
    }

    @RestController
    static class AnyPathController {

        @RequestMapping("/**")
        void handle() {}
    }
}
