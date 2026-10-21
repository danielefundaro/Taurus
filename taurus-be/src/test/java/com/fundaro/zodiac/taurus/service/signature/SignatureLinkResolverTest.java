package com.fundaro.zodiac.taurus.service.signature;

import com.fundaro.zodiac.taurus.domain.enumeration.TenantFeature;
import com.fundaro.zodiac.taurus.domain.signature.SignatureLinkRegistry;
import com.fundaro.zodiac.taurus.multitenancy.TenantContext;
import com.fundaro.zodiac.taurus.multitenancy.TenantSchemaRegistry;
import com.fundaro.zodiac.taurus.multitenancy.TenantTransactionExecutor;
import com.fundaro.zodiac.taurus.repository.signature.SignatureLinkRegistryRepository;
import com.fundaro.zodiac.taurus.service.TenantFeatureService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SignatureLinkResolverTest {

    @Mock
    SignatureLinkRateLimiter rateLimiter;
    @Mock
    SignatureLinkRegistryRepository registryRepository;
    @Mock
    TenantSchemaRegistry tenantSchemaRegistry;
    @Mock
    TenantFeatureService tenantFeatureService;
    @Mock
    TenantTransactionExecutor tenantTransactionExecutor;
    @Mock
    SignatureLinkService linkService;

    private final SignatureLinkTokens tokens = new SignatureLinkTokens();
    private final SignatureLinkTokens.Token token = tokens.generate();
    private final SignatureLinkRegistry route = new SignatureLinkRegistry();
    private final SignatureLinkSession session = new SignatureLinkSession("tenant-a", 5L, 30L, 10L, "abcd1234", ZonedDateTime.now().plusDays(7));
    private SignatureLinkResolver resolver;

    @BeforeEach
    void setUp() {
        route.setTenantId(4L);
        route.setLinkId(5L);
        when(rateLimiter.allowRequest(anyString())).thenReturn(true);
        when(rateLimiter.allowToken(anyString())).thenReturn(true);
        when(registryRepository.resolveActive(eq(token.digest()), any())).thenReturn(Optional.of(route));
        when(tenantFeatureService.isEnabledForTenant(4L, TenantFeature.INVENTORY_SIGNATURE)).thenReturn(true);
        when(tenantSchemaRegistry.findActiveTenantCode(4L)).thenReturn(Optional.of("tenant-a"));
        when(tenantTransactionExecutor.execute(eq("tenant-a"), any(Supplier.class))).thenAnswer(invocation -> invocation.<Supplier<?>>getArgument(1).get());
        when(linkService.open(eq(route), eq(token.digest()), eq("tenant-a"), any())).thenReturn(session);
        resolver = new SignatureLinkResolver(tokens, rateLimiter, registryRepository, tenantSchemaRegistry, tenantFeatureService, tenantTransactionExecutor, linkService);
    }

    @Test
    void runsTheActionForTheLinkInTheContextOfItsTenant() {
        String result = resolver.within(token.value(), "192.0.2.10", value -> value.actId() + "@" + TenantContext.getTenantCode().orElse("none"));

        assertThat(result).isEqualTo("10@tenant-a");
        assertThat(TenantContext.getTenantCode()).isEmpty();
    }

    @Test
    void answersTheSameWayForEveryUnusableLink() {
        assertThatThrownBy(() -> resolver.within("not-a-token", "192.0.2.10", value -> value)).isInstanceOf(SignatureLinkUnavailableException.class);
        assertThatThrownBy(() -> resolver.within(tokens.generate().value(), "192.0.2.10", value -> value)).isInstanceOf(SignatureLinkUnavailableException.class);

        when(tenantFeatureService.isEnabledForTenant(4L, TenantFeature.INVENTORY_SIGNATURE)).thenReturn(false);
        assertThatThrownBy(() -> resolver.within(token.value(), "192.0.2.10", value -> value)).isInstanceOf(SignatureLinkUnavailableException.class);

        when(tenantFeatureService.isEnabledForTenant(4L, TenantFeature.INVENTORY_SIGNATURE)).thenReturn(true);
        when(tenantSchemaRegistry.findActiveTenantCode(4L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> resolver.within(token.value(), "192.0.2.10", value -> value)).isInstanceOf(SignatureLinkUnavailableException.class);
        verify(linkService, never()).open(any(), any(), any(), any());
    }

    @Test
    void limitsTheRequestsBeforeReadingTheRegistry() {
        when(rateLimiter.allowRequest("192.0.2.10")).thenReturn(false);

        assertThatThrownBy(() -> resolver.within(token.value(), "192.0.2.10", value -> value)).isInstanceOf(SignatureLinkRateLimitException.class);
        verify(registryRepository, never()).resolveActive(any(), any());

        when(rateLimiter.allowRequest("192.0.2.10")).thenReturn(true);
        when(rateLimiter.allowToken(token.digest())).thenReturn(false);
        assertThatThrownBy(() -> resolver.within(token.value(), "192.0.2.10", value -> value)).isInstanceOf(SignatureLinkRateLimitException.class);
    }
}
