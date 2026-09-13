package com.fundaro.zodiac.taurus.service.calendarfeed;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fundaro.zodiac.taurus.config.ApplicationProperties;
import com.fundaro.zodiac.taurus.domain.Tenants;
import com.fundaro.zodiac.taurus.domain.calendarfeed.*;
import com.fundaro.zodiac.taurus.multitenancy.TenantContext;
import com.fundaro.zodiac.taurus.repository.TenantsRepository;
import com.fundaro.zodiac.taurus.repository.UsersRepository;
import com.fundaro.zodiac.taurus.repository.calendarfeed.*;
import com.fundaro.zodiac.taurus.service.dto.calendarfeed.CalendarFeedDtos.*;
import com.fundaro.zodiac.taurus.web.rest.errors.RequestAlertException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class CalendarFeedManagementServiceTest {
    @Test
    void replaysCreateWithTheSameKeyWithoutCreatingAnotherToken() {
        CalendarFeedSubscriptionRepository subscriptions = mock(CalendarFeedSubscriptionRepository.class);
        CalendarFeedTokenRegistryRepository registry = mock(CalendarFeedTokenRegistryRepository.class);
        CalendarFeedIdempotencyRepository idempotency = mock(CalendarFeedIdempotencyRepository.class);
        TenantsRepository tenants = mock(TenantsRepository.class);
        EntityManager entityManager = mock(EntityManager.class);
        Query lockQuery = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(lockQuery);
        when(lockQuery.setParameter(eq("lockKey"), anyLong())).thenReturn(lockQuery);
        when(lockQuery.getSingleResult()).thenReturn(1);

        Tenants tenant = new Tenants();
        tenant.setId(41L);
        when(tenants.findByCodeAndDeletedFalse("tenant-a")).thenReturn(Optional.of(tenant));

        AtomicReference<CalendarFeedSubscription> savedSubscription = new AtomicReference<>();
        AtomicReference<CalendarFeedIdempotency> savedReceipt = new AtomicReference<>();
        when(subscriptions.save(any())).thenAnswer(invocation -> {
            CalendarFeedSubscription value = invocation.getArgument(0);
            savedSubscription.set(value);
            return value;
        });
        when(subscriptions.findByIdAndDeletedFalse(any())).thenAnswer(invocation -> Optional.ofNullable(savedSubscription.get()));
        when(idempotency.findById(any())).thenAnswer(invocation -> Optional.ofNullable(savedReceipt.get()));
        when(idempotency.save(any())).thenAnswer(invocation -> {
            CalendarFeedIdempotency value = invocation.getArgument(0);
            savedReceipt.set(value);
            return value;
        });

        ApplicationProperties properties = new ApplicationProperties();
        properties.getCalendarFeed().setPublicBaseUrl("https://taurus.example");
        CalendarFeedManagementService service = new CalendarFeedManagementService(subscriptions, registry, idempotency,
            mock(UsersRepository.class), tenants, new CalendarFeedTokenService(), new CalendarFeedIdempotencyCodec(),
            entityManager, properties);
        JwtAuthenticationToken authentication = mock(JwtAuthenticationToken.class);
        when(authentication.getTokenAttributes()).thenReturn(Map.of("sub", "actor-1"));
        CreateRequest request = new CreateRequest("Tenant calendar", CalendarFeedScope.INTERNAL,
            CalendarFeedDetailLevel.MINIMAL, 90, 18, UUID.randomUUID());

        SecretFeed first = TenantContext.call("tenant-a", () -> service.createTenant(request, authentication));
        SecretFeed replay = TenantContext.call("tenant-a", () -> service.createTenant(request, authentication));

        assertThat(replay).isEqualTo(first);
        assertThat(savedReceipt.get().getTokenCiphertext()).isNotEqualTo(
            first.subscriptionUrl().substring(first.subscriptionUrl().lastIndexOf('/', first.subscriptionUrl().lastIndexOf('/') - 1) + 1,
                first.subscriptionUrl().lastIndexOf('/')).getBytes());
        verify(subscriptions, times(1)).save(any());
        verify(registry, times(1)).save(any());
        verify(idempotency, times(1)).save(any());
    }

    @Test
    void softDeletesARevokedFeedAndKeepsAuditInformation() {
        CalendarFeedSubscriptionRepository subscriptions = mock(CalendarFeedSubscriptionRepository.class);
        CalendarFeedSubscription feed = new CalendarFeedSubscription();
        feed.setId(UUID.randomUUID());
        feed.setStatus(CalendarFeedStatus.REVOKED);
        when(subscriptions.findByIdForUpdate(feed.getId())).thenReturn(Optional.of(feed));
        JwtAuthenticationToken authentication = authentication("admin-1");
        CalendarFeedManagementService service = service(subscriptions);

        service.deleteRevoked(feed.getId(), true, authentication);

        assertThat(feed.isDeleted()).isTrue();
        assertThat(feed.getEditDate()).isNotNull();
        assertThat(feed.getEditBy()).isEqualTo("admin-1");
        verify(subscriptions).save(feed);
    }

    @Test
    void refusesToSoftDeleteAnActiveFeed() {
        CalendarFeedSubscriptionRepository subscriptions = mock(CalendarFeedSubscriptionRepository.class);
        CalendarFeedSubscription feed = new CalendarFeedSubscription();
        feed.setId(UUID.randomUUID());
        feed.setStatus(CalendarFeedStatus.ACTIVE);
        when(subscriptions.findByIdForUpdate(feed.getId())).thenReturn(Optional.of(feed));
        CalendarFeedManagementService service = service(subscriptions);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.deleteRevoked(feed.getId(), true, authentication("admin-1")))
            .isInstanceOfSatisfying(RequestAlertException.class,
                error -> assertThat(error.getErrorKey()).isEqualTo("calendarFeed.active"));

        verify(subscriptions, never()).save(any());
    }

    private static CalendarFeedManagementService service(CalendarFeedSubscriptionRepository subscriptions) {
        ApplicationProperties properties = new ApplicationProperties();
        return new CalendarFeedManagementService(subscriptions, mock(CalendarFeedTokenRegistryRepository.class),
            mock(CalendarFeedIdempotencyRepository.class), mock(UsersRepository.class), mock(TenantsRepository.class),
            new CalendarFeedTokenService(), new CalendarFeedIdempotencyCodec(), mock(EntityManager.class), properties);
    }

    private static JwtAuthenticationToken authentication(String subject) {
        JwtAuthenticationToken authentication = mock(JwtAuthenticationToken.class);
        when(authentication.getTokenAttributes()).thenReturn(Map.of("sub", subject));
        return authentication;
    }
}
