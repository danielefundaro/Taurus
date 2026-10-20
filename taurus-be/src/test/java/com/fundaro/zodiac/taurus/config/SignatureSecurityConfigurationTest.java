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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
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
 * Every authenticated user signs their own acts through the listed personal endpoints only; switching the electronic
 * signature and verifying the evidence chain are administrative. The public page of the parents is reachable without
 * authentication through the listed paths only.
 */
@WebMvcTest(controllers = SignatureSecurityConfigurationTest.SignatureController.class)
@Import({SecurityConfiguration.class, SecurityProblemSupport.class, SignatureSecurityConfigurationTest.SignatureController.class})
@EnableConfigurationProperties(JHipsterProperties.class)
@TestPropertySource(properties = "spring.security.oauth2.client.provider.oidc.issuer-uri=https://issuer.example")
@SuppressWarnings("removal")
class SignatureSecurityConfigurationTest {

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
    void appliesTheSignatureAccessRules(String authority, HttpMethod method, String path, int expectedStatus) throws Exception {
        mockMvc
            .perform(request(method, path).with(user("member").authorities(new SimpleGrantedAuthority(authority))))
            .andExpect(status().is(expectedStatus));
    }

    @ParameterizedTest
    @MethodSource("personalPaths")
    void refusesAnonymousRequests(HttpMethod method, String path) throws Exception {
        mockMvc.perform(request(method, path).with(anonymous())).andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @MethodSource("linkPaths")
    void opensTheListedPathsOfThePublicPageOnly(HttpMethod method, String path, int expectedStatus) throws Exception {
        mockMvc.perform(request(method, path).with(anonymous())).andExpect(status().is(expectedStatus));
    }

    private static Stream<Arguments> linkPaths() {
        String base = "/api/signature-links/v1/" + "A".repeat(43);
        return Stream.of(
            Arguments.of(HttpMethod.GET, base, 200),
            Arguments.of(HttpMethod.PUT, base + "/holder-data", 200),
            Arguments.of(HttpMethod.GET, base + "/documents/MAIN", 200),
            Arguments.of(HttpMethod.GET, base + "/attestation", 200),
            Arguments.of(HttpMethod.POST, base + "/challenges", 200),
            Arguments.of(HttpMethod.POST, base + "/challenges/7/resend", 200),
            Arguments.of(HttpMethod.POST, base + "/challenges/7/confirm", 200),
            Arguments.of(HttpMethod.DELETE, base, 401),
            Arguments.of(HttpMethod.POST, base + "/documents/MAIN/ciesign", 200),
            Arguments.of(HttpMethod.GET, base + "/evidence", 401),
            Arguments.of(HttpMethod.POST, base + "/documents/MAIN/other", 401)
        );
    }

    private static Stream<Arguments> requests() {
        return Stream.of(
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.GET, "/api/user/signatures/methods", 200),
            Arguments.of(AuthoritiesConstants.USER_EXTERNAL, HttpMethod.GET, "/api/user/signatures/methods", 200),
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.PUT, "/api/user/signatures/methods", 200),
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.DELETE, "/api/user/signatures/methods", 403),
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.POST, "/api/user/signatures/challenges", 200),
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.POST, "/api/user/signatures/challenges/7/resend", 200),
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.POST, "/api/user/signatures/challenges/7/confirm", 200),
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.GET, "/api/user/signatures/challenges/7", 403),
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.POST, "/api/user/signatures/acts", 403),
            Arguments.of(AuthoritiesConstants.ADMIN, HttpMethod.POST, "/api/user/signatures/acts", 403),
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.POST, "/api/inventory/signatures/verify-chain", 403),
            Arguments.of(AuthoritiesConstants.ADMIN, HttpMethod.POST, "/api/inventory/signatures/verify-chain", 200),
            Arguments.of(AuthoritiesConstants.SUPER_ADMIN, HttpMethod.POST, "/api/inventory/signatures/verify-chain", 200),
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.PUT, "/api/inventory/loan-template/signature", 403),
            Arguments.of(AuthoritiesConstants.ADMIN, HttpMethod.PUT, "/api/inventory/loan-template/signature", 200),
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.GET, "/api/user/signatures/acts", 200),
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.GET, "/api/user/signatures/acts/7", 200),
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.PUT, "/api/user/signatures/acts/7/holder-data", 200),
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.GET, "/api/user/signatures/acts/7/documents/MAIN", 200),
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.GET, "/api/user/signatures/acts/7/attestation", 200),
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.POST, "/api/user/signatures/acts/7/documents/MAIN/ciesign", 200),
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.PUT, "/api/user/signatures/acts/7/documents/MAIN/ciesign", 403),
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.GET, "/api/user/signatures/acts/7/evidence", 403),
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.GET, "/api/inventory/loan-sheets/7/signature-acts", 403),
            Arguments.of(AuthoritiesConstants.ADMIN, HttpMethod.GET, "/api/inventory/loan-sheets/7/signature-acts", 200),
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.GET, "/api/inventory/loan-sheets/7/signature-dossier", 403),
            Arguments.of(AuthoritiesConstants.SUPER_ADMIN, HttpMethod.GET, "/api/inventory/loan-sheets/7/signature-dossier", 200),
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.PUT, "/api/inventory/signature-acts/7/association-signer", 403),
            Arguments.of(AuthoritiesConstants.ADMIN, HttpMethod.PUT, "/api/inventory/signature-acts/7/association-signer", 200),
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.POST, "/api/inventory/signature-acts/7/paper-declaration", 403),
            Arguments.of(AuthoritiesConstants.ADMIN, HttpMethod.POST, "/api/inventory/signature-acts/7/paper-declaration", 200),
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.POST, "/api/inventory/signature-acts/7/links/9/resend", 403),
            Arguments.of(AuthoritiesConstants.ADMIN, HttpMethod.POST, "/api/inventory/signature-acts/7/links/9/resend", 200),
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.DELETE, "/api/inventory/signature-acts/7/links/9", 403),
            Arguments.of(AuthoritiesConstants.SUPER_ADMIN, HttpMethod.DELETE, "/api/inventory/signature-acts/7/links/9", 200),
            Arguments.of(AuthoritiesConstants.USER, HttpMethod.GET, "/api/signature-links/v1/" + "A".repeat(43) + "/evidence", 403)
        );
    }

    private static Stream<Arguments> personalPaths() {
        return Stream.of(
            Arguments.of(HttpMethod.GET, "/api/user/signatures/methods"),
            Arguments.of(HttpMethod.PUT, "/api/user/signatures/methods"),
            Arguments.of(HttpMethod.POST, "/api/user/signatures/challenges"),
            Arguments.of(HttpMethod.POST, "/api/user/signatures/challenges/7/confirm"),
            Arguments.of(HttpMethod.GET, "/api/user/signatures/acts"),
            Arguments.of(HttpMethod.PUT, "/api/user/signatures/acts/7/holder-data"),
            Arguments.of(HttpMethod.POST, "/api/user/signatures/acts/7/documents/MAIN/ciesign")
        );
    }

    @RestController
    static class SignatureController {

        @GetMapping({
            "/api/user/signatures/methods",
            "/api/user/signatures/challenges/{id}",
            "/api/user/signatures/acts",
            "/api/user/signatures/acts/{id}",
            "/api/user/signatures/acts/{id}/documents/{role}",
            "/api/user/signatures/acts/{id}/attestation",
            "/api/user/signatures/acts/{id}/evidence",
            "/api/inventory/loan-sheets/{id}/signature-acts",
            "/api/inventory/loan-sheets/{id}/signature-dossier",
            "/api/signature-links/v1/{token}",
            "/api/signature-links/v1/{token}/documents/{role}",
            "/api/signature-links/v1/{token}/attestation",
            "/api/signature-links/v1/{token}/evidence"
        })
        void get() {
        }

        @DeleteMapping({"/api/inventory/signature-acts/{id}/links/{signerId}", "/api/signature-links/v1/{token}"})
        void delete() {
        }

        @PostMapping({
            "/api/user/signatures/challenges",
            "/api/user/signatures/challenges/{id}/resend",
            "/api/user/signatures/challenges/{id}/confirm",
            "/api/user/signatures/acts",
            "/api/user/signatures/acts/{id}/documents/{role}/ciesign",
            "/api/inventory/signatures/verify-chain",
            "/api/inventory/signature-acts/{id}/paper-declaration",
            "/api/inventory/signature-acts/{id}/links/{signerId}/resend",
            "/api/signature-links/v1/{token}/challenges",
            "/api/signature-links/v1/{token}/challenges/{id}/resend",
            "/api/signature-links/v1/{token}/challenges/{id}/confirm",
            "/api/signature-links/v1/{token}/documents/{role}/ciesign",
            "/api/signature-links/v1/{token}/documents/{role}/other"
        })
        void post() {
        }

        @PutMapping({
            "/api/user/signatures/methods",
            "/api/inventory/loan-template/signature",
            "/api/user/signatures/acts/{id}/holder-data",
            "/api/inventory/signature-acts/{id}/association-signer",
            "/api/signature-links/v1/{token}/holder-data"
        })
        void put() {
        }
    }
}
