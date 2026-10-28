package com.fundaro.zodiac.taurus.config;

import com.fundaro.zodiac.taurus.multitenancy.TenantProvisioningStatus;
import com.fundaro.zodiac.taurus.security.AuthoritiesConstants;
import com.fundaro.zodiac.taurus.service.TenantFeatureService;
import com.fundaro.zodiac.taurus.service.eventpreparation.EventPreparationService;
import com.fundaro.zodiac.taurus.web.rest.EventPreparationResource;
import com.fundaro.zodiac.taurus.web.rest.ExternalEventPreparationResource;
import com.fundaro.zodiac.taurus.web.rest.FinanceEventPreparationResource;
import com.fundaro.zodiac.taurus.web.rest.UserEventPreparationResource;
import com.fundaro.zodiac.taurus.web.rest.errors.SecurityProblemSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tech.jhipster.config.JHipsterProperties;

import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Il confine HTTP della preparazione evento: ogni ruolo raggiunge solo la propria vista e i propri comandi, le viste
 * non entrano in cache, il corpo dei salvataggi è validato prima del servizio e la motivazione di "nessun movimento
 * previsto" è facoltativa. Le regole sono quelle vere di {@link SecurityConfiguration} e delle annotazioni delle
 * risorse.
 */
@WebMvcTest(controllers = {
    EventPreparationResource.class,
    FinanceEventPreparationResource.class,
    UserEventPreparationResource.class,
    ExternalEventPreparationResource.class
})
@Import({
    SecurityConfiguration.class,
    SecurityProblemSupport.class,
    EventPreparationResource.class,
    FinanceEventPreparationResource.class,
    UserEventPreparationResource.class,
    ExternalEventPreparationResource.class
})
@EnableConfigurationProperties(JHipsterProperties.class)
@TestPropertySource(properties = {
    "spring.security.oauth2.client.provider.oidc.issuer-uri=https://issuer.example",
    "application.event-preparation.enabled=true"
})
@SuppressWarnings("removal")
class EventPreparationSecurityConfigurationTest {

    private static final String ADMIN_PATH = "/api/calendar-events/1/preparation";
    private static final String PROGRAM = "{\"entries\":[{\"trackId\":7,\"encore\":true}],\"version\":0}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EventPreparationService service;

    @MockBean
    private ClientRegistrationRepository clientRegistrationRepository;

    @MockBean
    private JwtDecoder jwtDecoder;

    @MockBean
    private TenantFeatureService tenantFeatureService;

    @MockBean
    private TenantProvisioningStatus tenantProvisioningStatus;

    @Test
    void anAnonymousRequestIsRefused() throws Exception {
        mockMvc.perform(request(HttpMethod.GET, ADMIN_PATH)).andExpect(status().isUnauthorized());
        mockMvc.perform(request(HttpMethod.GET, "/api/finance/events/1/preparation")).andExpect(status().isUnauthorized());
        mockMvc.perform(request(HttpMethod.GET, "/api/user/calendar-events/1/preparation")).andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @ParameterizedTest(name = "{0} {1} as {2} -> {3}")
    @MethodSource("boundary")
    void everyRoleReachesOnlyItsOwnViewsAndCommands(HttpMethod method, String path, String authority, int expected) throws Exception {
        MockHttpServletRequestBuilder builder = request(method, path).with(user("someone").authorities(new SimpleGrantedAuthority(authority)));
        if (path.endsWith("/program")) builder = builder.contentType(MediaType.APPLICATION_JSON).content(PROGRAM);
        if (path.endsWith("/materials")) builder = builder.contentType(MediaType.APPLICATION_JSON).content("{\"materials\":[],\"version\":0}");

        mockMvc.perform(builder).andExpect(status().is(expected));
    }

    @Test
    void theViewsAreNeverCached() throws Exception {
        var admin = user("someone").authorities(new SimpleGrantedAuthority(AuthoritiesConstants.ADMIN));
        var member = user("someone").authorities(new SimpleGrantedAuthority(AuthoritiesConstants.USER));

        mockMvc.perform(request(HttpMethod.GET, ADMIN_PATH).with(admin)).andExpect(header().string("Cache-Control", "no-store"));
        mockMvc.perform(request(HttpMethod.GET, ADMIN_PATH + "/catalogue").with(admin)).andExpect(header().string("Cache-Control", "no-store"));
        mockMvc.perform(request(HttpMethod.GET, "/api/user/calendar-events/1/preparation").with(member)).andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void theReasonOfNoMovementsIsOptionalAndBounded() throws Exception {
        var treasurer = user("someone").authorities(new SimpleGrantedAuthority(AuthoritiesConstants.TREASURER));
        var admin = user("someone").authorities(new SimpleGrantedAuthority(AuthoritiesConstants.ADMIN));

        mockMvc.perform(request(HttpMethod.POST, "/api/finance/events/1/no-movements-confirmation").with(treasurer)).andExpect(status().isOk());
        verify(service).confirmNoMovements(eq(1L), isNull(), any());
        mockMvc.perform(request(HttpMethod.POST, ADMIN_PATH + "/no-movements-confirmation").with(admin)
            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Evento gratuito\"}")).andExpect(status().isOk());
        verify(service).confirmNoMovements(eq(1L), eq("Evento gratuito"), any());
        mockMvc.perform(request(HttpMethod.POST, "/api/finance/events/1/no-movements-confirmation").with(treasurer)
            .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"" + "x".repeat(501) + "\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void savesAreValidatedBeforeTheService() throws Exception {
        var admin = user("someone").authorities(new SimpleGrantedAuthority(AuthoritiesConstants.ADMIN));
        String tooLong = "{\"entries\":[" + String.join(",", java.util.Collections.nCopies(101, "{\"trackId\":7}")) + "],\"version\":0}";

        mockMvc.perform(request(HttpMethod.PUT, ADMIN_PATH + "/program").with(admin).contentType(MediaType.APPLICATION_JSON).content(tooLong))
            .andExpect(status().isBadRequest());
        mockMvc.perform(request(HttpMethod.PUT, ADMIN_PATH + "/program").with(admin).contentType(MediaType.APPLICATION_JSON).content("{\"entries\":[]}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(request(HttpMethod.PUT, ADMIN_PATH + "/materials").with(admin).contentType(MediaType.APPLICATION_JSON).content("{\"materials\":[]}"))
            .andExpect(status().isBadRequest());
        mockMvc.perform(request(HttpMethod.PUT, ADMIN_PATH + "/configuration").with(admin).contentType(MediaType.APPLICATION_JSON)
            .content("{\"profile\":\"OTHER\",\"availabilityDeadlineMinutes\":0,\"materialsDeadlineMinutes\":0,\"version\":0}"))
            .andExpect(status().isBadRequest());
        verify(service, never()).replaceProgram(anyLong(), any(), any());
        verify(service, never()).replaceMaterials(anyLong(), any(), any());
        verify(service, never()).configure(anyLong(), any(), any());
        verify(service, never()).getPersonal(anyLong(), any(), anyBoolean());
    }

    private static Stream<Arguments> boundary() {
        String finance = "/api/finance/events/1";
        String personal = "/api/user/calendar-events/1/preparation";
        String external = "/api/external/calendar-events/1/preparation";
        return Stream.of(
            Arguments.of(HttpMethod.GET, ADMIN_PATH, AuthoritiesConstants.SUPER_ADMIN, 200),
            Arguments.of(HttpMethod.GET, ADMIN_PATH, AuthoritiesConstants.ADMIN, 200),
            Arguments.of(HttpMethod.GET, ADMIN_PATH, AuthoritiesConstants.ARCHIVIST, 403),
            Arguments.of(HttpMethod.GET, ADMIN_PATH, AuthoritiesConstants.TREASURER, 403),
            Arguments.of(HttpMethod.GET, ADMIN_PATH, AuthoritiesConstants.USER, 403),
            Arguments.of(HttpMethod.GET, ADMIN_PATH + "/catalogue", AuthoritiesConstants.ARCHIVIST, 200),
            Arguments.of(HttpMethod.GET, ADMIN_PATH + "/catalogue", AuthoritiesConstants.TREASURER, 403),
            Arguments.of(HttpMethod.PUT, ADMIN_PATH + "/program", AuthoritiesConstants.ARCHIVIST, 200),
            Arguments.of(HttpMethod.PUT, ADMIN_PATH + "/program", AuthoritiesConstants.TREASURER, 403),
            Arguments.of(HttpMethod.PUT, ADMIN_PATH + "/program", AuthoritiesConstants.USER, 403),
            Arguments.of(HttpMethod.PUT, ADMIN_PATH + "/materials", AuthoritiesConstants.ADMIN, 200),
            Arguments.of(HttpMethod.PUT, ADMIN_PATH + "/materials", AuthoritiesConstants.ARCHIVIST, 403),
            Arguments.of(HttpMethod.POST, ADMIN_PATH + "/materials/3/confirm", AuthoritiesConstants.ARCHIVIST, 403),
            Arguments.of(HttpMethod.POST, ADMIN_PATH + "/presence-confirmation", AuthoritiesConstants.TREASURER, 403),
            Arguments.of(HttpMethod.POST, ADMIN_PATH + "/budget-confirmation", AuthoritiesConstants.TREASURER, 403),
            Arguments.of(HttpMethod.GET, finance + "/preparation", AuthoritiesConstants.TREASURER, 200),
            Arguments.of(HttpMethod.GET, finance + "/preparation", AuthoritiesConstants.ARCHIVIST, 403),
            Arguments.of(HttpMethod.GET, finance + "/preparation", AuthoritiesConstants.USER, 403),
            Arguments.of(HttpMethod.POST, finance + "/budget-confirmation", AuthoritiesConstants.TREASURER, 200),
            Arguments.of(HttpMethod.POST, finance + "/no-movements-confirmation", AuthoritiesConstants.ARCHIVIST, 403),
            Arguments.of(HttpMethod.GET, personal, AuthoritiesConstants.USER, 200),
            Arguments.of(HttpMethod.GET, personal, AuthoritiesConstants.USER_EXTERNAL, 403),
            Arguments.of(HttpMethod.GET, external, AuthoritiesConstants.USER_EXTERNAL, 200),
            Arguments.of(HttpMethod.GET, external, AuthoritiesConstants.USER, 403)
        );
    }
}
