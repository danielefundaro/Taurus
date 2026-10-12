package com.fundaro.zodiac.taurus.config;

import com.fundaro.zodiac.taurus.multitenancy.TenantProvisioningStatus;
import com.fundaro.zodiac.taurus.security.AuthoritiesConstants;
import com.fundaro.zodiac.taurus.service.TenantFeatureService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import com.fundaro.zodiac.taurus.web.rest.errors.SecurityProblemSupport;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.jhipster.config.JHipsterProperties;

import java.util.stream.Stream;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Members only list and download their own loan sheets; every other loan sheet endpoint is administrative.
 */
@WebMvcTest(controllers = LoanSheetSecurityConfigurationTest.LoanSheetController.class)
@Import({SecurityConfiguration.class, SecurityProblemSupport.class, LoanSheetSecurityConfigurationTest.LoanSheetController.class})
@EnableConfigurationProperties(JHipsterProperties.class)
@TestPropertySource(properties = "spring.security.oauth2.client.provider.oidc.issuer-uri=https://issuer.example")
@SuppressWarnings("removal")
class LoanSheetSecurityConfigurationTest {

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

    @ParameterizedTest
    @MethodSource("requests")
    void appliesTheLoanSheetAccessRules(String authority, HttpMethod method, String path, int expectedStatus) throws Exception {
        mockMvc
            .perform(request(method, path).with(user("member").authorities(new SimpleGrantedAuthority(authority))))
            .andExpect(status().is(expectedStatus));
    }

    private static Stream<Arguments> requests() {
        return Stream.of(
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.GET, "/api/user/inventory/loan-sheets", 200),
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.GET, "/api/user/inventory/loan-sheets/7/pdf", 200),
            Arguments.of(AuthoritiesConstants.USER_EXTERNAL, HttpMethod.GET, "/api/user/inventory/loan-sheets/7/pdf", 200),
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.GET, "/api/user/inventory/loan-sheets/7", 403),
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.POST, "/api/user/inventory/loan-sheets", 403),
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.GET, "/api/inventory/users/42/loan-sheets", 403),
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.GET, "/api/inventory/users/42/loan-expiration-range", 403),
            Arguments.of(AuthoritiesConstants.ADMIN, HttpMethod.GET, "/api/inventory/users/42/loan-expiration-range", 200),
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.GET, "/api/inventory/loan-template", 403),
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.GET, "/api/inventory/loan-template/versions/3/preview", 403),
            Arguments.of(AuthoritiesConstants.ADMIN, HttpMethod.GET, "/api/inventory/users/42/loan-sheets", 200),
            Arguments.of(AuthoritiesConstants.ADMIN, HttpMethod.PUT, "/api/inventory/loan-template/durations", 200),
            Arguments.of(AuthoritiesConstants.ADMIN, HttpMethod.GET, "/api/inventory/loan-template/versions/3/preview", 200),
            Arguments.of(AuthoritiesConstants.SUPER_ADMIN, HttpMethod.GET, "/api/inventory/loan-template", 200)
        );
    }

    @RestController
    static class LoanSheetController {

        @GetMapping({
            "/api/user/inventory/loan-sheets",
            "/api/user/inventory/loan-sheets/{id}/pdf",
            "/api/user/inventory/loan-sheets/{id}",
            "/api/inventory/users/{userIndex}/loan-sheets",
            "/api/inventory/users/{userIndex}/loan-expiration-range",
            "/api/inventory/loan-template",
            "/api/inventory/loan-template/versions/{id}/preview"
        })
        void get() {
        }

        @PostMapping("/api/user/inventory/loan-sheets")
        void post() {
        }

        @PutMapping("/api/inventory/loan-template/durations")
        void put() {
        }
    }
}
