package com.fundaro.zodiac.taurus.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.multitenancy.TenantContext;
import com.fundaro.zodiac.taurus.service.dashboard.DashboardOperationProvider;
import com.fundaro.zodiac.taurus.service.dashboard.DashboardRequestContext;
import com.fundaro.zodiac.taurus.service.dto.dashboard.DashboardDomain;
import com.fundaro.zodiac.taurus.service.dto.dashboard.DashboardOperationType;
import com.fundaro.zodiac.taurus.service.dto.dashboard.DashboardResultStatus;
import com.fundaro.zodiac.taurus.service.dto.dashboard.DashboardSeverity;
import com.fundaro.zodiac.taurus.service.dto.dashboard.OperationalItemDTO;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class OperationalDashboardServiceTest {

    @Mock TenantTimeZoneService timeZoneService;
    @Mock DashboardMetrics metrics;

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
            var result = service.getOperations(authentication());

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
        DashboardOperationProvider failing = new DashboardOperationProvider() {
            @Override
            public DashboardDomain domain() { return DashboardDomain.FINANCE; }
            @Override
            public List<OperationalItemDTO> getOperations(DashboardRequestContext context) { throw new IllegalStateException("database unavailable"); }
        };
        when(timeZoneService.currentZoneId()).thenReturn(ZoneId.of("Europe/Rome"));
        OperationalDashboardService service = service(List.of(calendar, failing));

        try (TenantContext.Scope ignored = TenantContext.use("tenant-a")) {
            var result = service.getOperations(authentication());

            assertThat(result.status()).isEqualTo(DashboardResultStatus.PARTIAL);
            assertThat(result.unavailableDomains()).containsExactly(DashboardDomain.FINANCE);
            assertThat(result.items()).hasSize(1);
            verify(metrics).recordProviderFailure(DashboardDomain.FINANCE);
        }
    }

    @Test
    void rejectsTargetsOutsideTheTypeAllowlist() {
        OperationalItemDTO malicious = item(
            DashboardOperationType.LEGAL_ACCEPTANCE_REQUIRED,
            DashboardDomain.LEGAL,
            DashboardSeverity.DANGER,
            "//example.test/steal"
        );

        assertThatThrownBy(() -> OperationalDashboardService.validate(malicious, DashboardDomain.LEGAL))
            .isInstanceOf(IllegalArgumentException.class);
    }

    private OperationalDashboardService service(List<DashboardOperationProvider> providers) {
        ApplicationProperties properties = new ApplicationProperties();
        properties.getDashboard().setEnabled(true);
        return new OperationalDashboardService(providers, timeZoneService, metrics, properties);
    }

    private static DashboardOperationProvider provider(DashboardDomain domain, OperationalItemDTO item) {
        return new DashboardOperationProvider() {
            @Override
            public DashboardDomain domain() { return domain; }
            @Override
            public List<OperationalItemDTO> getOperations(DashboardRequestContext context) { return List.of(item); }
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

    private static JwtAuthenticationToken authentication() {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token")
            .header("alg", "none")
            .subject("user-1")
            .claim("tenant", "tenant-a")
            .issuedAt(now)
            .expiresAt(now.plusSeconds(300))
            .build();
        return new JwtAuthenticationToken(jwt);
    }
}
