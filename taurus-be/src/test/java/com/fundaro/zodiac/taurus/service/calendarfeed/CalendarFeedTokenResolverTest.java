package com.fundaro.zodiac.taurus.service.calendarfeed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.calendarfeed.*;
import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaRegistry;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import com.fundaro.zodiac.taurus.repository.calendarfeed.*;
import com.fundaro.zodiac.taurus.service.TenantFeatureService;
import com.fundaro.zodiac.taurus.service.dto.calendarfeed.CalendarFeedDtos.Download;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

/**
 * Two behaviours of the public download that a real database would not show: the refusal when the
 * calendar would exceed the component limit, and the approximate last access, which is written
 * after the response and must never turn a correct calendar into an error.
 */
class CalendarFeedTokenResolverTest {

    private final CalendarFeedTokenService tokens = new CalendarFeedTokenService();
    private final CalendarFeedTokenRegistryRepository registry = mock(CalendarFeedTokenRegistryRepository.class);
    private final TenantSchemaRegistry schemas = mock(TenantSchemaRegistry.class);
    private final TenantTransactionExecutor transactions = mock(TenantTransactionExecutor.class);
    private final CalendarFeedSubscriptionRepository subscriptions = mock(CalendarFeedSubscriptionRepository.class);
    private final CalendarFeedEventRepository events = mock(CalendarFeedEventRepository.class);
    private final CalendarEventFeedTombstoneRepository tombstones = mock(CalendarEventFeedTombstoneRepository.class);
    private final CalendarFeedRateLimiter rateLimiter = mock(CalendarFeedRateLimiter.class);
    private final TenantFeatureService features = mock(TenantFeatureService.class);
    private final ApplicationProperties properties = new ApplicationProperties();

    @Test
    void refusesToServeACalendarBeyondTheComponentLimit() {
        properties.getCalendarFeed().setMaxComponents(1);
        CalendarFeedTokenService.Token token = prepare();
        List<CalendarEventFeedProjection> two = List.of(projection("Prova"), projection("Concerto"));
        when(events.findVisible(any(), any(), any())).thenReturn(two);

        CalendarFeedTokenResolver resolver = resolver();

        // Il server non tronca mai in silenzio: chiede una finestra più piccola con un 503.
        assertThatThrownBy(() -> resolver.resolve(token.value(), "203.0.113.7"))
            .isInstanceOf(CalendarFeedUnavailableException.class);
        verify(subscriptions, never()).save(any());
    }

    @Test
    void keepsTheCalendarWhenTheLastAccessCannotBeWritten() {
        CalendarFeedTokenService.Token token = prepare();
        List<CalendarEventFeedProjection> one = List.of(projection("Prova"));
        when(events.findVisible(any(), any(), any())).thenReturn(one);
        // La prima transazione genera il calendario, la seconda è quella dell'ultimo accesso.
        when(transactions.execute(anyString(), any(Supplier.class)))
            .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(1).get())
            .thenThrow(new IllegalStateException("tenant schema unavailable"));

        Optional<Download> download = resolver().resolve(token.value(), "203.0.113.7");

        assertThat(download).isPresent();
        assertThat(new String(download.orElseThrow().body(), StandardCharsets.UTF_8)).contains("SUMMARY:Prova");
    }

    private CalendarFeedTokenResolver resolver() {
        return new CalendarFeedTokenResolver(tokens, registry, schemas, transactions, subscriptions, events, tombstones,
            new IcalendarRenderer(properties), rateLimiter, properties, features, new CalendarFeedMetrics(new SimpleMeterRegistry()));
    }

    /** Un token valido che risolve su una sottoscrizione attiva di un tenant servito. */
    private CalendarFeedTokenService.Token prepare() {
        CalendarFeedTokenService.Token token = tokens.generate();
        UUID subscriptionId = UUID.randomUUID();

        CalendarFeedTokenRegistry route = new CalendarFeedTokenRegistry();
        route.setTokenDigest(token.digest());
        route.setSubscriptionId(subscriptionId);
        route.setTenantId(1L);
        route.setTokenVersion(1);
        route.setStatus(CalendarFeedStatus.ACTIVE);

        CalendarFeedSubscription subscription = new CalendarFeedSubscription();
        subscription.setId(subscriptionId);
        subscription.setName("Feed");
        subscription.setFeedType(CalendarFeedType.TENANT);
        subscription.setVisibilityScope(CalendarFeedScope.INTERNAL);
        subscription.setDetailLevel(CalendarFeedDetailLevel.MINIMAL);
        subscription.setPastDays(90);
        subscription.setFutureMonths(18);
        subscription.setStatus(CalendarFeedStatus.ACTIVE);
        subscription.setTokenVersion(1);
        subscription.setTokenFingerprint(tokens.fingerprint(token.digest()));
        subscription.setEditDate(Instant.parse("2026-10-01T08:00:00Z"));

        when(rateLimiter.allowRequest(any())).thenReturn(true);
        when(rateLimiter.allowToken(any())).thenReturn(true);
        when(registry.resolveActive(any())).thenReturn(Optional.of(route));
        when(features.isEnabledForTenant(eq(1L), any())).thenReturn(true);
        when(schemas.findActiveTenantCode(1L)).thenReturn(Optional.of("tenant_uno"));
        when(subscriptions.findByIdAndDeletedFalse(subscriptionId)).thenReturn(Optional.of(subscription));
        when(tombstones.findByAudienceAndExpiresAtAfterAndOriginalEndDateGreaterThanEqualAndOriginalStartDateLessThanEqualOrderByOriginalStartDate(
            any(), any(), any(), any())).thenReturn(List.of());
        lenient().when(transactions.execute(anyString(), any(Supplier.class)))
            .thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(1).get());
        return token;
    }

    private static CalendarEventFeedProjection projection(String summary) {
        Instant start = Instant.now().plus(2, ChronoUnit.DAYS);
        CalendarEventFeedProjection projection = mock(CalendarEventFeedProjection.class);
        when(projection.getUid()).thenReturn(UUID.randomUUID());
        lenient().when(projection.getSequence()).thenReturn(0);
        lenient().when(projection.getModifiedAt()).thenReturn(Date.from(start.minus(1, ChronoUnit.DAYS)));
        lenient().when(projection.getStartAt()).thenReturn(Date.from(start));
        lenient().when(projection.getEndAt()).thenReturn(Date.from(start.plus(2, ChronoUnit.HOURS)));
        lenient().when(projection.getSummary()).thenReturn(summary);
        lenient().when(projection.getLocation()).thenReturn(null);
        lenient().when(projection.getDescription()).thenReturn(null);
        return projection;
    }
}
