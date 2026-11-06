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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.jhipster.config.JHipsterProperties;

import java.util.stream.Stream;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Anyone reads the published versions of the legal documents, without login, through the two listed paths only;
 * every user confirms the documents in force, and only the super administrator manages the versions.
 */
@WebMvcTest(controllers = LegalSecurityConfigurationTest.LegalController.class)
@Import({SecurityConfiguration.class, SecurityProblemSupport.class, LegalSecurityConfigurationTest.LegalController.class})
@EnableConfigurationProperties(JHipsterProperties.class)
@TestPropertySource(properties = "spring.security.oauth2.client.provider.oidc.issuer-uri=https://issuer.example")
@SuppressWarnings("removal")
class LegalSecurityConfigurationTest {

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
    @MethodSource("publicPaths")
    void opensThePublishedVersionsToAnyone(HttpMethod method, String path, int expectedStatus) throws Exception {
        mockMvc.perform(request(method, path).with(anonymous())).andExpect(status().is(expectedStatus));
    }

    @ParameterizedTest
    @MethodSource("requests")
    void appliesTheLegalAccessRules(String authority, HttpMethod method, String path, int expectedStatus) throws Exception {
        mockMvc
            .perform(request(method, path).with(user("member").authorities(new SimpleGrantedAuthority(authority))))
            .andExpect(status().is(expectedStatus));
    }

    private static Stream<Arguments> publicPaths() {
        return Stream.of(
            Arguments.of(HttpMethod.GET, "/api/legal-documents/v1/terms", 200),
            Arguments.of(HttpMethod.GET, "/api/legal-documents/v1/privacy/1.3", 200),
            Arguments.of(HttpMethod.POST, "/api/legal-documents/v1/terms", 401),
            Arguments.of(HttpMethod.GET, "/api/legal-documents/v1/terms/1.3/source", 401),
            Arguments.of(HttpMethod.GET, "/api/legal-documents/v2/terms", 401),
            Arguments.of(HttpMethod.GET, "/api/legal/status", 401),
            Arguments.of(HttpMethod.GET, "/api/legal/documents", 401),
            Arguments.of(HttpMethod.POST, "/api/legal/documents/preview", 401)
        );
    }

    private static Stream<Arguments> requests() {
        return Stream.of(
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.GET, "/api/legal/status", 200),
            Arguments.of(AuthoritiesConstants.USER_EXTERNAL, HttpMethod.POST, "/api/legal/acceptances", 200),
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.GET, "/api/legal-documents/v1/terms", 200),
            Arguments.of(AuthoritiesConstants.ADMIN, HttpMethod.GET, "/api/legal/documents", 403),
            Arguments.of(AuthoritiesConstants.BOARD, HttpMethod.GET, "/api/legal/documents/7", 403),
            Arguments.of(AuthoritiesConstants.ADMIN, HttpMethod.POST, "/api/legal/documents", 403),
            Arguments.of(AuthoritiesConstants.ADMIN, HttpMethod.PUT, "/api/legal/documents/7", 403),
            Arguments.of(AuthoritiesConstants.ADMIN, HttpMethod.DELETE, "/api/legal/documents/7", 403),
            Arguments.of(AuthoritiesConstants.ADMIN, HttpMethod.POST, "/api/legal/documents/7/activation", 403),
            Arguments.of(AuthoritiesConstants.ADMIN, HttpMethod.DELETE, "/api/legal/documents/7/activation", 403),
            Arguments.of(AuthoritiesConstants.ADMIN, HttpMethod.POST, "/api/legal/documents/preview", 403),
            Arguments.of(AuthoritiesConstants.SUPER_ADMIN, HttpMethod.GET, "/api/legal/documents", 200),
            Arguments.of(AuthoritiesConstants.SUPER_ADMIN, HttpMethod.GET, "/api/legal/documents/7", 200),
            Arguments.of(AuthoritiesConstants.SUPER_ADMIN, HttpMethod.POST, "/api/legal/documents", 200),
            Arguments.of(AuthoritiesConstants.SUPER_ADMIN, HttpMethod.PUT, "/api/legal/documents/7", 200),
            Arguments.of(AuthoritiesConstants.SUPER_ADMIN, HttpMethod.DELETE, "/api/legal/documents/7", 200),
            Arguments.of(AuthoritiesConstants.SUPER_ADMIN, HttpMethod.POST, "/api/legal/documents/7/activation", 200),
            Arguments.of(AuthoritiesConstants.SUPER_ADMIN, HttpMethod.DELETE, "/api/legal/documents/7/activation", 200),
            Arguments.of(AuthoritiesConstants.SUPER_ADMIN, HttpMethod.POST, "/api/legal/documents/preview", 200),
            Arguments.of(AuthoritiesConstants.SUPER_ADMIN, HttpMethod.POST, "/api/legal-documents/v1/terms", 403)
        );
    }

    @RestController
    static class LegalController {

        @GetMapping({
            "/api/legal/status",
            "/api/legal/documents",
            "/api/legal/documents/{id}",
            "/api/legal-documents/v1/{type}",
            "/api/legal-documents/v1/{type}/{version}",
            "/api/legal-documents/v1/{type}/{version}/source",
            "/api/legal-documents/v2/{type}"
        })
        void get() {
        }

        @PostMapping({
            "/api/legal/acceptances",
            "/api/legal/documents",
            "/api/legal/documents/{id}/activation",
            "/api/legal/documents/preview",
            "/api/legal-documents/v1/{type}"
        })
        void post() {
        }

        @PutMapping("/api/legal/documents/{id}")
        void put() {
        }

        @DeleteMapping({"/api/legal/documents/{id}", "/api/legal/documents/{id}/activation"})
        void delete() {
        }
    }
}
