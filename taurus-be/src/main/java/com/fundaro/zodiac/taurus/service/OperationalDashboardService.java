package com.fundaro.zodiac.taurus.service;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.multitenancy.TenantContext;
import com.fundaro.zodiac.taurus.security.SecurityUtils;
import com.fundaro.zodiac.taurus.service.dashboard.DashboardOperationProvider;
import com.fundaro.zodiac.taurus.service.dashboard.DashboardRequestContext;
import com.fundaro.zodiac.taurus.service.dto.dashboard.DashboardDomain;
import com.fundaro.zodiac.taurus.service.dto.dashboard.DashboardOperationType;
import com.fundaro.zodiac.taurus.service.dto.dashboard.DashboardResultStatus;
import com.fundaro.zodiac.taurus.service.dto.dashboard.DashboardSeverity;
import com.fundaro.zodiac.taurus.service.dto.dashboard.OperationalDashboardDTO;
import com.fundaro.zodiac.taurus.service.dto.dashboard.OperationalItemDTO;
import com.fundaro.zodiac.taurus.service.dto.dashboard.OperationalSummaryDTO;
import com.fundaro.zodiac.taurus.domain.enumeration.TenantFeature;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

@Service
public class OperationalDashboardService {

    private static final Logger LOG = LoggerFactory.getLogger(OperationalDashboardService.class);
    private static final Map<DashboardOperationType, Set<String>> TARGET_PATHS = targetPaths();
    private static final Comparator<OperationalItemDTO> ITEM_ORDER = Comparator
        .comparingInt((OperationalItemDTO item) -> severityOrder(item.severity()))
        .thenComparing(OperationalItemDTO::dueAt, Comparator.nullsLast(Comparator.naturalOrder()))
        .thenComparingInt(item -> item.domain().ordinal())
        .thenComparing(item -> item.type().name());

    private final List<DashboardOperationProvider> providers;
    private final TenantTimeZoneService tenantTimeZoneService;
    private final DashboardMetrics metrics;
    private final ApplicationProperties.DashboardProperties properties;
    private final TenantFeatureService tenantFeatureService;

    public OperationalDashboardService(
        List<DashboardOperationProvider> providers,
        TenantTimeZoneService tenantTimeZoneService,
        DashboardMetrics metrics,
        ApplicationProperties applicationProperties,
        TenantFeatureService tenantFeatureService
    ) {
        this.providers = List.copyOf(providers);
        this.tenantTimeZoneService = tenantTimeZoneService;
        this.metrics = metrics;
        this.properties = applicationProperties.getDashboard();
        this.tenantFeatureService = tenantFeatureService;
    }

    public OperationalDashboardDTO getOperations(AbstractAuthenticationToken authentication) {
        long startedAt = System.nanoTime();
        try {
            return buildOperations(authentication, startedAt);
        } catch (RuntimeException exception) {
            metrics.recordFailure(System.nanoTime() - startedAt);
            throw exception;
        }
    }

    private OperationalDashboardDTO buildOperations(AbstractAuthenticationToken authentication, long startedAt) {
        String subject = required(SecurityUtils.getUserIdFromAuthentication(authentication), "user", HttpStatus.UNAUTHORIZED);
        String tenant = required(SecurityUtils.getTenantIdFromAuthentication(authentication), "tenant", HttpStatus.BAD_REQUEST);
        String activeTenant = TenantContext.getTenantCode().orElseThrow(() ->
            new RequestAlertException(HttpStatus.BAD_REQUEST, "Tenant context is required", "OperationalDashboard", "tenant.missing")
        );
        if (!tenant.equals(activeTenant)) {
            throw new RequestAlertException(HttpStatus.FORBIDDEN, "Tenant context mismatch", "OperationalDashboard", "tenant.mismatch");
        }
        ZoneId zoneId = tenantTimeZoneService.currentZoneId();
        ZonedDateTime generatedAt = ZonedDateTime.now(zoneId);
        Set<String> authorities = authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).collect(java.util.stream.Collectors.toUnmodifiableSet());
        DashboardRequestContext context = new DashboardRequestContext(subject, authorities, generatedAt, zoneId, authentication);

        if (!properties.isEnabled()) {
            OperationalDashboardDTO disabled = complete(generatedAt, List.of(), List.of());
            metrics.recordRequest(disabled.status(), 0, System.nanoTime() - startedAt);
            return disabled;
        }

        Map<DashboardOperationType, OperationalItemDTO> uniqueItems = new LinkedHashMap<>();
        EnumSet<DashboardDomain> unavailable = EnumSet.noneOf(DashboardDomain.class);
        for (DashboardOperationProvider provider : providers) {
            if (provider.domain() == DashboardDomain.INVENTORY && !tenantFeatureService.isEnabled(TenantFeature.INVENTORY)) continue;
            if (provider.domain() == DashboardDomain.FINANCE && !tenantFeatureService.isEnabled(TenantFeature.FINANCE)) continue;
            long providerStartedAt = System.nanoTime();
            try {
                for (OperationalItemDTO item : provider.getOperations(context)) {
                    validate(item, provider.domain());
                    uniqueItems.putIfAbsent(item.type(), item);
                }
                LOG.debug(
                    "dashboard_provider_complete tenant={} domain={} durationMs={}",
                    activeTenant,
                    provider.domain(),
                    (System.nanoTime() - providerStartedAt) / 1_000_000
                );
            } catch (RuntimeException exception) {
                unavailable.add(provider.domain());
                metrics.recordProviderFailure(provider.domain());
                LOG.warn(
                    "dashboard_provider_failed tenant={} domain={} durationMs={} errorClass={}",
                    activeTenant,
                    provider.domain(),
                    (System.nanoTime() - providerStartedAt) / 1_000_000,
                    exception.getClass().getName()
                );
            }
        }
        List<OperationalItemDTO> items = new ArrayList<>(uniqueItems.values());
        items.sort(ITEM_ORDER);
        OperationalDashboardDTO result = complete(generatedAt, items, List.copyOf(unavailable));
        metrics.recordRequest(result.status(), items.size(), System.nanoTime() - startedAt);
        return result;
    }

    private static OperationalDashboardDTO complete(
        ZonedDateTime generatedAt,
        List<OperationalItemDTO> items,
        List<DashboardDomain> unavailableDomains
    ) {
        int danger = (int) items.stream().filter(item -> item.severity() == DashboardSeverity.DANGER).count();
        int warning = (int) items.stream().filter(item -> item.severity() == DashboardSeverity.WARNING).count();
        int info = (int) items.stream().filter(item -> item.severity() == DashboardSeverity.INFO).count();
        DashboardResultStatus status = unavailableDomains.isEmpty() ? DashboardResultStatus.COMPLETE : DashboardResultStatus.PARTIAL;
        return new OperationalDashboardDTO(
            generatedAt,
            status,
            new OperationalSummaryDTO(items.size(), danger, warning, info),
            List.copyOf(items),
            unavailableDomains
        );
    }

    static void validate(OperationalItemDTO item, DashboardDomain providerDomain) {
        if (item == null || item.type() == null || item.domain() != providerDomain || item.count() <= 0) {
            throw new IllegalArgumentException("Invalid dashboard operation");
        }
        Set<String> expectedPaths = TARGET_PATHS.get(item.type());
        if (!item.type().name().equals(item.key()) || expectedPaths == null || !expectedPaths.contains(item.targetPath())) {
            throw new IllegalArgumentException("Invalid dashboard operation identity or target path");
        }
        if (!item.targetPath().startsWith("/") || item.targetPath().startsWith("//")) {
            throw new IllegalArgumentException("Dashboard target path must be internal");
        }
    }

    private static String required(String value, String claim, HttpStatus status) {
        if (value == null || value.isBlank()) {
            throw new RequestAlertException(status, "Missing " + claim + " claim", "OperationalDashboard", "dashboard.token.claim.missing");
        }
        return value;
    }

    private static int severityOrder(DashboardSeverity severity) {
        return switch (severity) {
            case DANGER -> 0;
            case WARNING -> 1;
            case INFO -> 2;
        };
    }

    private static Map<DashboardOperationType, Set<String>> targetPaths() {
        Map<DashboardOperationType, Set<String>> paths = new EnumMap<>(DashboardOperationType.class);
        paths.put(DashboardOperationType.LEGAL_ACCEPTANCE_REQUIRED, Set.of("/legal/accept"));
        paths.put(DashboardOperationType.CALENDAR_AVAILABILITY_REQUIRED, Set.of("/calendar?attention=my-missing-availability"));
        paths.put(DashboardOperationType.CALENDAR_RESPONSES_MISSING, Set.of("/calendar?attention=missing-availability"));
        paths.put(DashboardOperationType.INVENTORY_DECISION_REQUIRED, Set.of("/inventory?view=mine&attention=pending-decisions"));
        paths.put(DashboardOperationType.INVENTORY_DECISIONS_PENDING, Set.of("/inventory?attention=pending-decisions"));
        paths.put(DashboardOperationType.INVENTORY_RETURNS_PENDING, Set.of("/inventory?attention=pending-returns"));
        paths.put(
            DashboardOperationType.INVENTORY_ASSIGNMENTS_EXPIRING,
            Set.of("/inventory?attention=expiring", "/inventory?view=mine&attention=expiring")
        );
        paths.put(DashboardOperationType.FINANCE_MOVEMENTS_UNRECONCILED, Set.of("/finance?section=movements&reconciled=false"));
        paths.put(DashboardOperationType.NOTIFICATION_DELIVERY_FAILED, Set.of("/admin/notification-delivery?status=FAILED"));
        return Map.copyOf(paths);
    }
}
