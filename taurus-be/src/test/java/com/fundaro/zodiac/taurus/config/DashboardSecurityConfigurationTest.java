package com.fundaro.zodiac.taurus.config;

import com.fundaro.zodiac.taurus.multitenancy.TenantProvisioningStatus;
import com.fundaro.zodiac.taurus.security.AuthoritiesConstants;
import com.fundaro.zodiac.taurus.service.OperationalDashboardService;
import com.fundaro.zodiac.taurus.service.TenantFeatureService;
import com.fundaro.zodiac.taurus.service.dto.dashboard.DashboardResultStatus;
import com.fundaro.zodiac.taurus.service.dto.dashboard.OperationalDashboardDTO;
import com.fundaro.zodiac.taurus.service.dto.dashboard.OperationalSummaryDTO;
import com.fundaro.zodiac.taurus.web.rest.OperationalDashboardResource;
import com.fundaro.zodiac.taurus.web.rest.errors.SecurityProblemSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tech.jhipster.config.JHipsterProperties;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La dashboard operativa è raggiungibile da ogni utente autenticato, mentre l'elenco degli eventi dietro le righe di
 * preparazione è amministrativo. Le regole sono quelle vere di {@link SecurityConfiguration}, sulla risorsa vera: le
 * verifiche per autorità restano nei provider, qui si controlla il confine HTTP.
 */
@WebMvcTest(controllers = OperationalDashboardResource.class)
@Import({SecurityConfiguration.class, SecurityProblemSupport.class, OperationalDashboardResource.class})
@EnableConfigurationProperties(JHipsterProperties.class)
@TestPropertySource(properties = "spring.security.oauth2.client.provider.oidc.issuer-uri=https://issuer.example")
@SuppressWarnings("removal")
class DashboardSecurityConfigurationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OperationalDashboardService service;

    @MockBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private TenantFeatureService tenantFeatureService;

    @MockBean
    private TenantProvisioningStatus tenantProvisioningStatus;

    @BeforeEach
    void answerWithAnEmptyDashboard() {
        when(service.getOperations(any())).thenReturn(
            new OperationalDashboardDTO(ZonedDateTime.now(), DashboardResultStatus.COMPLETE, new OperationalSummaryDTO(0, 0, 0, 0), List.of(), List.of())
        );
        when(service.getAttentionEvents(any(), any())).thenReturn(List.of(3L));
    }

    @Test
    void anAnonymousRequestIsRefused() throws Exception {
        mockMvc.perform(get("/api/dashboard/operations")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/dashboard/operations/calendar-events").param("attention", "event-preparation")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyRole")
    void everyAuthenticatedRoleReachesTheDashboardWithoutCaching(String authority) throws Exception {
        mockMvc
            .perform(get("/api/dashboard/operations").with(user("someone").authorities(new SimpleGrantedAuthority(authority))))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.status").value("COMPLETE"));
    }

    @Test
    void noParameterSelectsTenantOrUser() throws Exception {
        mockMvc
            .perform(
                get("/api/dashboard/operations")
                    .param("tenant", "other-tenant")
                    .param("userId", "other-user")
                    .param("subject", "other-user")
                    .with(user("someone").authorities(new SimpleGrantedAuthority(AuthoritiesConstants.ADMIN)))
            )
            .andExpect(status().isOk());

        // L'unico ingresso del servizio è l'autenticazione: i parametri della richiesta non arrivano a nessun provider.
        verify(service).getOperations(any(AbstractAuthenticationToken.class));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("calendarFilter")
    void theCalendarFilterIsReservedToAdministrators(String authority, int expectedStatus) throws Exception {
        mockMvc
            .perform(
                get("/api/dashboard/operations/calendar-events")
                    .param("attention", "event-follow-up")
                    .with(user("someone").authorities(new SimpleGrantedAuthority(authority)))
            )
            .andExpect(status().is(expectedStatus));
    }

    @Test
    void theCalendarFilterAnswersWithTheEventIdentifiersWithoutCaching() throws Exception {
        mockMvc
            .perform(
                get("/api/dashboard/operations/calendar-events")
                    .param("attention", "event-preparation")
                    .with(user("someone").authorities(new SimpleGrantedAuthority(AuthoritiesConstants.SUPER_ADMIN)))
            )
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$[0]").value(3));

        verify(service).getAttentionEvents(any(AbstractAuthenticationToken.class), eq("event-preparation"));
    }

    @Test
    void theOtherMethodsAndPathsOfTheDashboardAreClosed() throws Exception {
        var admin = user("someone").authorities(new SimpleGrantedAuthority(AuthoritiesConstants.ADMIN));

        mockMvc.perform(post("/api/dashboard/operations").with(admin)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/dashboard/anything").with(admin)).andExpect(status().isForbidden());
    }

    private static Stream<String> everyRole() {
        return Stream.of(
            AuthoritiesConstants.SUPER_ADMIN,
            AuthoritiesConstants.ADMIN,
            AuthoritiesConstants.TREASURER,
            AuthoritiesConstants.ARCHIVIST,
            AuthoritiesConstants.USER,
            AuthoritiesConstants.USER_EXTERNAL
        );
    }

    private static Stream<Arguments> calendarFilter() {
        return Stream.of(
            Arguments.of(AuthoritiesConstants.SUPER_ADMIN, 200),
            Arguments.of(AuthoritiesConstants.ADMIN, 200),
            Arguments.of(AuthoritiesConstants.TREASURER, 403),
            Arguments.of(AuthoritiesConstants.ARCHIVIST, 403),
            Arguments.of(AuthoritiesConstants.USER, 403),
            Arguments.of(AuthoritiesConstants.USER_EXTERNAL, 403)
        );
    }
}
