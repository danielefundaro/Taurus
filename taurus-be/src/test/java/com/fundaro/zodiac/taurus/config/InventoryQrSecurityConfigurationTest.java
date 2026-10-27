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

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La scansione è aperta a ogni utente autenticato, che vede soltanto ciò che il resolver gli concede; i soci
 * raggiungono solo le segnalazioni e le fotografie delle proprie consegne; etichette, rotazione, transizioni e
 * rimozione delle fotografie restano amministrative.
 */
@WebMvcTest(controllers = InventoryQrSecurityConfigurationTest.InventoryQrController.class)
@Import({SecurityConfiguration.class, SecurityProblemSupport.class, InventoryQrSecurityConfigurationTest.InventoryQrController.class})
@EnableConfigurationProperties(JHipsterProperties.class)
@TestPropertySource(properties = "spring.security.oauth2.client.provider.oidc.issuer-uri=https://issuer.example")
@SuppressWarnings("removal")
class InventoryQrSecurityConfigurationTest {

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
    void appliesTheInventoryQrAccessRules(String authority, HttpMethod method, String path, int expectedStatus) throws Exception {
        mockMvc
            .perform(request(method, path).with(user("member").authorities(new SimpleGrantedAuthority(authority))).with(csrf()))
            .andExpect(status().is(expectedStatus));
    }

    @ParameterizedTest
    @MethodSource("anonymousRequests")
    void anAnonymousScanRevealsNothing(String path) throws Exception {
        mockMvc.perform(request(HttpMethod.GET, path).with(anonymous())).andExpect(status().isUnauthorized());
    }

    private static Stream<Arguments> requests() {
        String code = "/api/inventory-scan/v1/550e8400-e29b-41d4-a716-446655440000";
        return Stream.of(
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.GET, code, 200),
            Arguments.of(AuthoritiesConstants.USER_EXTERNAL, HttpMethod.GET, code, 200),
            Arguments.of(AuthoritiesConstants.ADMIN, HttpMethod.GET, code, 200),
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.GET, "/api/user/inventory/assignments/7/issues", 200),
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.POST, "/api/user/inventory/assignments/7/issues", 200),
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.GET, "/api/user/inventory/issues/9", 200),
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.POST, "/api/user/inventory/issues/9/photos", 200),
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.GET, "/api/user/inventory/issue-photos/5", 200),
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.DELETE, "/api/user/inventory/issue-photos/5", 403),
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.PATCH, "/api/user/inventory/issues/9/status", 403),
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.GET, "/api/inventory/items/42/issues", 403),
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.PATCH, "/api/inventory/issues/9/status", 403),
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.DELETE, "/api/inventory/issue-photos/5", 403),
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.POST, "/api/inventory/labels", 403),
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.POST, "/api/inventory/items/42/qr-code/rotate", 403),
            Arguments.of(AuthoritiesConstants.USER_EXTERNAL, HttpMethod.GET, "/api/inventory/items/42/qr-code.png", 403),
            Arguments.of(AuthoritiesConstants.ADMIN, HttpMethod.DELETE, "/api/inventory/issue-photos/5", 200),
            Arguments.of(AuthoritiesConstants.ADMIN, HttpMethod.PATCH, "/api/inventory/issues/9/status", 200),
            Arguments.of(AuthoritiesConstants.ADMIN, HttpMethod.POST, "/api/inventory/labels", 200),
            Arguments.of(AuthoritiesConstants.SUPER_ADMIN, HttpMethod.POST, "/api/inventory/items/42/qr-code/rotate", 200)
        );
    }

    private static Stream<String> anonymousRequests() {
        return Stream.of("/api/inventory-scan/v1/550e8400-e29b-41d4-a716-446655440000", "/api/user/inventory/assignments/7/issues");
    }

    @RestController
    static class InventoryQrController {

        @GetMapping({
            "/api/inventory-scan/v1/{publicId}",
            "/api/user/inventory/assignments/{id}/issues",
            "/api/user/inventory/issues/{id}",
            "/api/user/inventory/issue-photos/{id}",
            "/api/inventory/items/{id}/issues",
            "/api/inventory/items/{id}/qr-code.png"
        })
        void get() {
        }

        @PostMapping({
            "/api/user/inventory/assignments/{id}/issues",
            "/api/user/inventory/issues/{id}/photos",
            "/api/inventory/labels",
            "/api/inventory/items/{id}/qr-code/rotate"
        })
        void post() {
        }

        @PatchMapping({"/api/user/inventory/issues/{id}/status", "/api/inventory/issues/{id}/status"})
        void patch() {
        }

        @DeleteMapping({"/api/user/inventory/issue-photos/{id}", "/api/inventory/issue-photos/{id}"})
        void delete() {
        }
    }
}
