package com.fundaro.zodiac.taurus.config;

import com.fundaro.zodiac.taurus.multitenancy.TenantProvisioningStatus;
import com.fundaro.zodiac.taurus.security.AuthoritiesConstants;
import com.fundaro.zodiac.taurus.service.TenantFeatureService;
import com.fundaro.zodiac.taurus.web.rest.errors.ErrorConstants;
import com.fundaro.zodiac.taurus.web.rest.errors.SecurityProblemSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tech.jhipster.config.JHipsterProperties;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La method security è attiva: un {@code @PreAuthorize} restringe anche un percorso che le regole HTTP di
 * {@link SecurityConfiguration} lasciano a ogni utente autenticato, e il rifiuto conserva la forma standard degli
 * errori. Il controller di prova esiste solo in questo test.
 */
@WebMvcTest(controllers = MethodSecurityConfigurationTest.ProbeResource.class)
@Import({SecurityConfiguration.class, SecurityProblemSupport.class, MethodSecurityConfigurationTest.ProbeResource.class})
@EnableConfigurationProperties(JHipsterProperties.class)
@TestPropertySource(properties = "spring.security.oauth2.client.provider.oidc.issuer-uri=https://issuer.example")
@SuppressWarnings("removal")
class MethodSecurityConfigurationTest {

    private static final String PROBE = "/api/method-security-probe";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TenantFeatureService tenantFeatureService;

    @MockBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private TenantProvisioningStatus tenantProvisioningStatus;

    @Test
    void theAnnotationNarrowsAPathOpenToEveryAuthenticatedUser() throws Exception {
        mockMvc
            .perform(get(PROBE).with(user("someone").authorities(new SimpleGrantedAuthority(AuthoritiesConstants.SUPER_ADMIN))))
            .andExpect(status().isOk());
        mockMvc
            .perform(get(PROBE).with(user("someone").authorities(new SimpleGrantedAuthority(AuthoritiesConstants.ADMIN))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message").value(ErrorConstants.MESSAGE_PREFIX + ErrorConstants.FORBIDDEN));
    }

    @Test
    void anAnonymousRequestStopsAtTheHttpRules() throws Exception {
        mockMvc.perform(get(PROBE)).andExpect(status().isUnauthorized());
    }

    @RestController
    public static class ProbeResource {

        @GetMapping(PROBE)
        @PreAuthorize("hasAuthority('ROLE_SUPER_ADMIN')")
        public String probe() {
            return "ok";
        }
    }
}
