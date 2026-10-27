package com.fundaro.zodiac.taurus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.enumeration.TenantFeature;
import com.fundaro.zodiac.taurus.multitenancy.TenantContext;
import com.fundaro.zodiac.taurus.security.AuthoritiesConstants;
import com.fundaro.zodiac.taurus.service.dashboard.CalendarOperationProvider;
import com.fundaro.zodiac.taurus.service.dashboard.CalendarOperationProvider.CalendarAttention;
import com.fundaro.zodiac.taurus.service.dashboard.DashboardOperationProvider;
import com.fundaro.zodiac.taurus.service.dashboard.DashboardRequestContext;
import com.fundaro.zodiac.taurus.service.dto.dashboard.DashboardDomain;
import com.fundaro.zodiac.taurus.service.dto.dashboard.DashboardOperationType;
import com.fundaro.zodiac.taurus.service.dto.dashboard.DashboardResultStatus;
import com.fundaro.zodiac.taurus.service.dto.dashboard.DashboardSeverity;
import com.fundaro.zodiac.taurus.service.dto.dashboard.OperationalItemDTO;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class OperationalDashboardServiceTest {

    @Mock
    TenantTimeZoneService timeZoneService;
    @Mock
    DashboardMetrics metrics;
    @Mock
    TenantFeatureService tenantFeatureService;
    @Mock
    CalendarOperationProvider calendarOperations;

    @Test
    void ordersItemsAndBuildsAConsistentSummary() {
        DashboardOperationProvider finance = provider(
            DashboardDomain.FINANCE,
            item(DashboardOperationType.FINANCE_MOVEMENTS_UNRECONCILED, DashboardDomain.FINANCE, DashboardSeverity.INFO, "/finance?section=movements&reconciled=false")
        );
        DashboardOperationProvider legal = provider(
            DashboardDomain.LEGAL,
            item(DashboardOperationType.LEGAL_ACCEPTANCE_REQUIRED, DashboardDomain.LEGAL, DashboardSeverity.DANGER, "/legal/accept")
        );
        DashboardOperationProvider inventory = provider(
            DashboardDomain.INVENTORY,
            item(DashboardOperationType.INVENTORY_DECISIONS_PENDING, DashboardDomain.INVENTORY, DashboardSeverity.WARNING, "/inventory?attention=pending-decisions")
        );
        when(timeZoneService.currentZoneId()).thenReturn(ZoneId.of("Europe/Rome"));
        OperationalDashboardService service = service(List.of(finance, inventory, legal));

        try (TenantContext.Scope ignored = TenantContext.use("tenant-a")) {
            var result = service.getOperations(authentication("tenant-a"));

            assertThat(result.status()).isEqualTo(DashboardResultStatus.COMPLETE);
            assertThat(result.items()).extracting(OperationalItemDTO::severity)
                .containsExactly(DashboardSeverity.DANGER, DashboardSeverity.WARNING, DashboardSeverity.INFO);
            assertThat(result.summary().groupCount()).isEqualTo(3);
            assertThat(result.summary().dangerCount()).isEqualTo(1);
            assertThat(result.summary().warningCount()).isEqualTo(1);
            assertThat(result.summary().infoCount()).isEqualTo(1);
        }
    }

    @Test
    void keepsSuccessfulDomainsWhenOneProviderFails() {
        DashboardOperationProvider calendar = provider(
            DashboardDomain.CALENDAR,
            item(DashboardOperationType.CALENDAR_AVAILABILITY_REQUIRED, DashboardDomain.CALENDAR, DashboardSeverity.WARNING, "/calendar?attention=my-missing-availability")
        );
        when(timeZoneService.currentZoneId()).thenReturn(ZoneId.of("Europe/Rome"));
        OperationalDashboardService service = service(List.of(calendar, failing(DashboardDomain.FINANCE)));

        try (TenantContext.Scope ignored = TenantContext.use("tenant-a")) {
            var result = service.getOperations(authentication("tenant-a"));

            assertThat(result.status()).isEqualTo(DashboardResultStatus.PARTIAL);
            assertThat(result.unavailableDomains()).containsExactly(DashboardDomain.FINANCE);
            assertThat(result.items()).hasSize(1);
            verify(metrics).recordProviderFailure(DashboardDomain.FINANCE);
        }
    }

    @Test
    void aPartialResponseCarriesNoTechnicalMessage() throws Exception {
        when(timeZoneService.currentZoneId()).thenReturn(ZoneId.of("Europe/Rome"));
        OperationalDashboardService service = service(List.of(failing(DashboardDomain.FINANCE)));

        try (TenantContext.Scope ignored = TenantContext.use("tenant-a")) {
            String json = new ObjectMapper().registerModule(new JavaTimeModule()).writeValueAsString(service.getOperations(authentication("tenant-a")));

            assertThat(json).contains("\"PARTIAL\"", "\"FINANCE\"");
            assertThat(json).doesNotContain("database unavailable", "IllegalStateException", "Exception", "at com.");
        }
    }

    @Test
    void aMissingIdentityIsAnAuthenticationErrorNotAPartialResult() {
        OperationalDashboardService service = service(List.of());
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").claim("tenant", "tenant-a").claim("scope", "x").build();

        try (TenantContext.Scope ignored = TenantContext.use("tenant-a")) {
            assertThatThrownBy(() -> service.getOperations(new JwtAuthenticationToken(jwt)))
                .isInstanceOf(RequestAlertException.class)
                .satisfies(error -> {
                    assertThat(((RequestAlertException) error).getErrorKey()).isEqualTo("auth.identity.missing");
                    assertThat(((RequestAlertException) error).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                });
        }
        verify(metrics).recordFailure(anyLong());
    }

    @Test
    void aTokenWithoutTenantIsRefused() {
        OperationalDashboardService service = service(List.of());
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "none").subject("user-1").build();

        try (TenantContext.Scope ignored = TenantContext.use("tenant-a")) {
            assertThatThrownBy(() -> service.getOperations(new JwtAuthenticationToken(jwt)))
                .isInstanceOf(RequestAlertException.class)
                .extracting(error -> ((RequestAlertException) error).getErrorKey())
                .isEqualTo("auth.tenant.missing");
        }
    }

    @Test
    void anAdministratorCannotReadATenantOtherThanTheActiveOne() {
        DashboardOperationProvider calendar = provider(
            DashboardDomain.CALENDAR,
            item(DashboardOperationType.CALENDAR_RESPONSES_MISSING, DashboardDomain.CALENDAR, DashboardSeverity.WARNING, "/calendar?attention=missing-availability")
        );
        OperationalDashboardService service = service(List.of(calendar));

        try (TenantContext.Scope ignored = TenantContext.use("tenant-b")) {
            assertThatThrownBy(() -> service.getOperations(authentication("tenant-a", AuthoritiesConstants.ADMIN)))
                .isInstanceOf(RequestAlertException.class)
                .satisfies(error -> {
                    assertThat(((RequestAlertException) error).getErrorKey()).isEqualTo("auth.tenant.mismatch");
                    assertThat(((RequestAlertException) error).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                });
            assertThatThrownBy(() -> service.getAttentionEvents(authentication("tenant-a", AuthoritiesConstants.ADMIN), "event-preparation"))
                .isInstanceOf(RequestAlertException.class)
                .extracting(error -> ((RequestAlertException) error).getErrorKey())
                .isEqualTo("auth.tenant.mismatch");
        }
        verifyNoInteractions(calendarOperations);
    }

    @Test
    void aTenantChangeInTheSameSessionNeverReusesThePreviousResult() {
        AtomicInteger calls = new AtomicInteger();
        DashboardOperationProvider perTenant = new DashboardOperationProvider() {
            @Override
            public DashboardDomain domain() {
                return DashboardDomain.CALENDAR;
            }

            @Override
            public List<OperationalItemDTO> getOperations(DashboardRequestContext context) {
                calls.incrementAndGet();
                return TenantContext.getTenantCode().orElseThrow().equals("tenant-a")
                    ? List.of(item(DashboardOperationType.CALENDAR_AVAILABILITY_REQUIRED, DashboardDomain.CALENDAR, DashboardSeverity.WARNING, "/calendar?attention=my-missing-availability"))
                    : List.of();
            }
        };
        when(timeZoneService.currentZoneId()).thenReturn(ZoneId.of("Europe/Rome"));
        OperationalDashboardService service = service(List.of(perTenant));

        try (TenantContext.Scope ignored = TenantContext.use("tenant-a")) {
            assertThat(service.getOperations(authentication("tenant-a")).items()).hasSize(1);
        }
        try (TenantContext.Scope ignored = TenantContext.use("tenant-b")) {
            assertThat(service.getOperations(authentication("tenant-b")).items()).isEmpty();
        }
        assertThat(calls).hasValue(2);
    }

    @Test
    void theDisabledFlagAnswersCompleteWithoutAskingTheProviders() {
        AtomicInteger calls = new AtomicInteger();
        DashboardOperationProvider counting = new DashboardOperationProvider() {
            @Override
            public DashboardDomain domain() {
                return DashboardDomain.CALENDAR;
            }

            @Override
            public List<OperationalItemDTO> getOperations(DashboardRequestContext context) {
                calls.incrementAndGet();
                return List.of();
            }
        };
        when(timeZoneService.currentZoneId()).thenReturn(ZoneId.of("Europe/Rome"));
        ApplicationProperties properties = new ApplicationProperties();
        OperationalDashboardService service = new OperationalDashboardService(List.of(counting), timeZoneService, metrics, properties, tenantFeatureService, calendarOperations);

        try (TenantContext.Scope ignored = TenantContext.use("tenant-a")) {
            var result = service.getOperations(authentication("tenant-a"));

            assertThat(result.status()).isEqualTo(DashboardResultStatus.COMPLETE);
            assertThat(result.items()).isEmpty();
        }
        assertThat(calls).hasValue(0);
    }

    @Test
    void skipsInventoryAndFinanceWithoutTheirTenantFeatures() {
        List<DashboardDomain> asked = new ArrayList<>();
        List<DashboardOperationProvider> providers = List.of(DashboardDomain.INVENTORY, DashboardDomain.FINANCE, DashboardDomain.CALENDAR).stream()
            .map(domain -> (DashboardOperationProvider) new DashboardOperationProvider() {
                @Override
                public DashboardDomain domain() {
                    return domain;
                }

                @Override
                public List<OperationalItemDTO> getOperations(DashboardRequestContext context) {
                    asked.add(domain);
                    return List.of();
                }
            })
            .toList();
        when(timeZoneService.currentZoneId()).thenReturn(ZoneId.of("Europe/Rome"));
        ApplicationProperties properties = new ApplicationProperties();
        properties.getDashboard().setEnabled(true);
        when(tenantFeatureService.isEnabled(TenantFeature.INVENTORY)).thenReturn(false);
        when(tenantFeatureService.isEnabled(TenantFeature.FINANCE)).thenReturn(false);
        OperationalDashboardService service = new OperationalDashboardService(providers, timeZoneService, metrics, properties, tenantFeatureService, calendarOperations);

        try (TenantContext.Scope ignored = TenantContext.use("tenant-a")) {
            assertThat(service.getOperations(authentication("tenant-a")).status()).isEqualTo(DashboardResultStatus.COMPLETE);
        }
        assertThat(asked).containsExactly(DashboardDomain.CALENDAR);
    }

    @Test
    void keepsASingleRowPerOperationType() {
        OperationalItemDTO first = item(DashboardOperationType.CALENDAR_AVAILABILITY_REQUIRED, DashboardDomain.CALENDAR, DashboardSeverity.WARNING, "/calendar?attention=my-missing-availability");
        DashboardOperationProvider duplicated = new DashboardOperationProvider() {
            @Override
            public DashboardDomain domain() {
                return DashboardDomain.CALENDAR;
            }

            @Override
            public List<OperationalItemDTO> getOperations(DashboardRequestContext context) {
                return List.of(first, first);
            }
        };
        when(timeZoneService.currentZoneId()).thenReturn(ZoneId.of("Europe/Rome"));
        OperationalDashboardService service = service(List.of(duplicated));

        try (TenantContext.Scope ignored = TenantContext.use("tenant-a")) {
            var result = service.getOperations(authentication("tenant-a"));

            assertThat(result.items()).hasSize(1);
            assertThat(result.summary().groupCount()).isEqualTo(1);
        }
    }

    @Test
    void anItemWithAnotherTargetMakesItsDomainUnavailable() {
        DashboardOperationProvider hijacked = provider(
            DashboardDomain.CALENDAR,
            item(DashboardOperationType.CALENDAR_AVAILABILITY_REQUIRED, DashboardDomain.CALENDAR, DashboardSeverity.WARNING, "/admin/notification-delivery?status=FAILED")
        );
        when(timeZoneService.currentZoneId()).thenReturn(ZoneId.of("Europe/Rome"));
        OperationalDashboardService service = service(List.of(hijacked));

        try (TenantContext.Scope ignored = TenantContext.use("tenant-a")) {
            var result = service.getOperations(authentication("tenant-a"));

            assertThat(result.status()).isEqualTo(DashboardResultStatus.PARTIAL);
            assertThat(result.items()).isEmpty();
            assertThat(result.unavailableDomains()).containsExactly(DashboardDomain.CALENDAR);
        }
    }

    @Test
    void rejectsTargetsOutsideTheTypeAllowlist() {
        for (String target : List.of("//example.test/steal", "https://example.test/steal", "javascript:alert(1)", "/legal/accept?next=//example.test")) {
            OperationalItemDTO malicious = item(DashboardOperationType.LEGAL_ACCEPTANCE_REQUIRED, DashboardDomain.LEGAL, DashboardSeverity.DANGER, target);

            assertThatThrownBy(() -> OperationalDashboardService.validate(malicious, DashboardDomain.LEGAL))
                .as(target)
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void acceptsTheInventoryIssuesWithTheirOwnTargets() {
        OperationalItemDTO unsafe = item(
            DashboardOperationType.INVENTORY_UNSAFE_ISSUES,
            DashboardDomain.INVENTORY,
            DashboardSeverity.DANGER,
            "/inventory?attention=issues-unsafe"
        );
        OperationalItemDTO limiting = item(
            DashboardOperationType.INVENTORY_LIMITING_ISSUES,
            DashboardDomain.INVENTORY,
            DashboardSeverity.WARNING,
            "/inventory?attention=issues-limiting"
        );

        OperationalDashboardService.validate(unsafe, DashboardDomain.INVENTORY);
        OperationalDashboardService.validate(limiting, DashboardDomain.INVENTORY);
    }

    @Test
    void definesTheTargetsOfEveryOperationType() {
        for (DashboardOperationType type : DashboardOperationType.values()) {
            assertThat(OperationalDashboardService.allowedTargets(type)).as(type.name()).isNotEmpty();
        }
    }

    @Test
    void theCalendarFilterAsksTheCalendarProviderWithTheIdentityOfTheToken() {
        when(timeZoneService.currentZoneId()).thenReturn(ZoneId.of("Europe/Rome"));
        when(calendarOperations.attentionEventIds(any(), eq(CalendarAttention.EVENT_FOLLOW_UP))).thenReturn(List.of(4L, 2L));
        OperationalDashboardService service = service(List.of());

        try (TenantContext.Scope ignored = TenantContext.use("tenant-a")) {
            assertThat(service.getAttentionEvents(authentication("tenant-a", AuthoritiesConstants.ADMIN), "event-follow-up")).containsExactly(4L, 2L);
        }
        ArgumentCaptor<DashboardRequestContext> context = ArgumentCaptor.forClass(DashboardRequestContext.class);
        verify(calendarOperations).attentionEventIds(context.capture(), eq(CalendarAttention.EVENT_FOLLOW_UP));
        assertThat(context.getValue().subject()).isEqualTo("user-1");
        assertThat(context.getValue().authorities()).containsExactly(AuthoritiesConstants.ADMIN);
    }

    @Test
    void theCalendarFilterRefusesTheOtherAttentionValues() {
        OperationalDashboardService service = service(List.of());

        try (TenantContext.Scope ignored = TenantContext.use("tenant-a")) {
            for (String attention : java.util.Arrays.asList("missing-availability", "EVENT_PREPARATION", "", null)) {
                assertThatThrownBy(() -> service.getAttentionEvents(authentication("tenant-a", AuthoritiesConstants.ADMIN), attention))
                    .as(String.valueOf(attention))
                    .isInstanceOf(RequestAlertException.class)
                    .satisfies(error -> {
                        assertThat(((RequestAlertException) error).getErrorKey()).isEqualTo("dashboard.attention.invalid");
                        assertThat(((RequestAlertException) error).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    });
            }
        }
        verify(calendarOperations, never()).attentionEventIds(any(), any());
    }

    private OperationalDashboardService service(List<DashboardOperationProvider> providers) {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getDashboard().setEnabled(true);
        org.mockito.Mockito.lenient().when(tenantFeatureService.isEnabled(TenantFeature.FINANCE)).thenReturn(true);
        org.mockito.Mockito.lenient().when(tenantFeatureService.isEnabled(TenantFeature.INVENTORY)).thenReturn(true);
        return new OperationalDashboardService(providers, timeZoneService, metrics, properties, tenantFeatureService, calendarOperations);
    }

    private static DashboardOperationProvider provider(DashboardDomain domain, OperationalItemDTO item) {
        return new DashboardOperationProvider() {
            @Override
            public DashboardDomain domain() {
                return domain;
            }

            @Override
            public List<OperationalItemDTO> getOperations(DashboardRequestContext context) {
                return List.of(item);
            }
        };
    }

    private static DashboardOperationProvider failing(DashboardDomain domain) {
        return new DashboardOperationProvider() {
            @Override
            public DashboardDomain domain() {
                return domain;
            }

            @Override
            public List<OperationalItemDTO> getOperations(DashboardRequestContext context) {
                throw new IllegalStateException("database unavailable");
            }
        };
    }

    private static OperationalItemDTO item(
        DashboardOperationType type,
        DashboardDomain domain,
        DashboardSeverity severity,
        String targetPath
    ) {
        return new OperationalItemDTO(type.name(), type, domain, severity, 1, null, "Titolo", "Descrizione", null, "Apri", targetPath);
    }

    private static JwtAuthenticationToken authentication(String tenant, String... authorities) {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject("user-1")
            .claim("tenant", tenant)
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .build();
        return new JwtAuthenticationToken(jwt, java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
    }
}
