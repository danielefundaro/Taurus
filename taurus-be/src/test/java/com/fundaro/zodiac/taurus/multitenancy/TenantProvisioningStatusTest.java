package com.fundaro.zodiac.taurus.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class TenantProvisioningStatusTest {

    private final TenantSchemaRegistry registry = mock(TenantSchemaRegistry.class);
    private final AtomicLong now = new AtomicLong();
    private final TenantProvisioningStatus status = new TenantProvisioningStatus(registry, now::get);

    @Test
    void reportsTenantsWithoutActiveSchemaAsNotProvisioned() {
        when(registry.isActive("temp")).thenReturn(false);

        assertThat(status.isProvisioned("temp")).isFalse();
    }

    @Test
    void treatsAMissingTenantCodeAsNotProvisioned() {
        assertThat(status.isProvisioned(null)).isFalse();
        assertThat(status.isProvisioned(" ")).isFalse();
        verifyNoInteractions(registry);
    }

    @Test
    void cachesActiveTenantsUntilTheTtlExpires() {
        when(registry.isActive("acme")).thenReturn(true, false);

        assertThat(status.isProvisioned("acme")).isTrue();
        now.addAndGet(TenantProvisioningStatus.CACHE_TTL.toNanos() - 1);
        assertThat(status.isProvisioned("acme")).isTrue();
        verify(registry, times(1)).isActive("acme");

        now.addAndGet(1);
        assertThat(status.isProvisioned("acme")).isFalse();
        verify(registry, times(2)).isActive("acme");
    }

    @Test
    void seesATenantAsSoonAsItsSchemaBecomesActive() {
        when(registry.isActive("acme")).thenReturn(false, true);

        assertThat(status.isProvisioned("acme")).isFalse();
        assertThat(status.isProvisioned("acme")).isTrue();
    }
}
