package com.fundaro.zodiac.taurus.service.calendarfeed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.calendarfeed.*;
import com.fundaro.zodiac.taurus.multitenancy.*;
import com.fundaro.zodiac.taurus.repository.calendarfeed.*;
import com.fundaro.zodiac.taurus.service.dto.calendarfeed.CalendarFeedDtos.Download;
import java.util.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

class CalendarFeedTenantIsolationTest {
    @Test
    void routesAValidTokenOnlyToTheTenantRecordedInTheGlobalRegistry() {
        CalendarFeedTokenService tokenService = new CalendarFeedTokenService();
        CalendarFeedTokenService.Token token = tokenService.generate();
        CalendarFeedTokenRegistry route = new CalendarFeedTokenRegistry();
        route.setTenantId(41L);
        route.setSubscriptionId(UUID.randomUUID());
        route.setTokenVersion(1);
        route.setStatus(CalendarFeedStatus.ACTIVE);

        CalendarFeedTokenRegistryRepository registry = mock(CalendarFeedTokenRegistryRepository.class);
        TenantSchemaRegistry schemas = mock(TenantSchemaRegistry.class);
        TenantTransactionExecutor transactions = mock(TenantTransactionExecutor.class);
        CalendarFeedSubscriptionRepository subscriptions = mock(CalendarFeedSubscriptionRepository.class);
        CalendarFeedEventRepository events = mock(CalendarFeedEventRepository.class);
        CalendarEventFeedTombstoneRepository tombstones = mock(CalendarEventFeedTombstoneRepository.class);
        IcalendarRenderer renderer = mock(IcalendarRenderer.class);
        CalendarFeedRateLimiter limiter = mock(CalendarFeedRateLimiter.class);
        ApplicationProperties properties = new ApplicationProperties();

        when(registry.resolveActive(any(byte[].class))).thenReturn(Optional.of(route));
        when(schemas.findActiveTenantCode(41L)).thenReturn(Optional.of("tenant-a"));
        when(limiter.allowRequest("192.0.2.1")).thenReturn(true);
        when(limiter.allowToken(any(byte[].class))).thenReturn(true);
        when(transactions.execute(eq("tenant-a"), ArgumentMatchers.<Supplier<Optional<Download>>>any()))
            .thenAnswer(invocation -> invocation.<Supplier<Optional<Download>>>getArgument(1).get());
        when(subscriptions.findByIdAndDeletedFalse(route.getSubscriptionId())).thenReturn(Optional.empty());

        CalendarFeedTokenResolver resolver = new CalendarFeedTokenResolver(tokenService, registry, schemas, transactions,
            subscriptions, events, tombstones, renderer, limiter, properties);

        assertThat(resolver.resolve(token.value(), "192.0.2.1")).isEmpty();
        verify(transactions).execute(eq("tenant-a"), ArgumentMatchers.<Supplier<Optional<Download>>>any());
        verify(transactions, never()).execute(eq("tenant-b"), ArgumentMatchers.<Supplier<Optional<Download>>>any());
        verify(subscriptions).findByIdAndDeletedFalse(route.getSubscriptionId());
        verifyNoInteractions(events, tombstones, renderer);
    }

    @Test
    void neverEntersATenantTransactionWhenTheRegistryTenantIsNotActive() {
        CalendarFeedTokenService tokenService = new CalendarFeedTokenService();
        CalendarFeedTokenService.Token token = tokenService.generate();
        CalendarFeedTokenRegistry route = new CalendarFeedTokenRegistry();
        route.setTenantId(41L);
        CalendarFeedTokenRegistryRepository registry = mock(CalendarFeedTokenRegistryRepository.class);
        TenantSchemaRegistry schemas = mock(TenantSchemaRegistry.class);
        TenantTransactionExecutor transactions = mock(TenantTransactionExecutor.class);
        CalendarFeedRateLimiter limiter = mock(CalendarFeedRateLimiter.class);
        when(registry.resolveActive(any(byte[].class))).thenReturn(Optional.of(route));
        when(schemas.findActiveTenantCode(41L)).thenReturn(Optional.empty());
        when(limiter.allowRequest(anyString())).thenReturn(true);
        when(limiter.allowToken(any(byte[].class))).thenReturn(true);

        CalendarFeedTokenResolver resolver = new CalendarFeedTokenResolver(tokenService, registry, schemas, transactions,
            mock(CalendarFeedSubscriptionRepository.class), mock(CalendarFeedEventRepository.class),
            mock(CalendarEventFeedTombstoneRepository.class), mock(IcalendarRenderer.class), limiter, new ApplicationProperties());

        assertThat(resolver.resolve(token.value(), "192.0.2.1")).isEmpty();
        verifyNoInteractions(transactions);
    }
}
