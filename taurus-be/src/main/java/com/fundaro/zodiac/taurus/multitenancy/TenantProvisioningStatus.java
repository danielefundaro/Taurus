package com.fundaro.zodiac.taurus.multitenancy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Tells whether a tenant has an active PostgreSQL schema.
 * <p>
 * Only positive answers are cached, so a tenant becomes usable as soon as its provisioning completes. A deleted
 * tenant is seen as not provisioned at the latest after {@link #CACHE_TTL}.
 */
@Component
public class TenantProvisioningStatus {

    static final Duration CACHE_TTL = Duration.ofSeconds(30);

    private final TenantSchemaRegistry registry;
    private final LongSupplier nanoTime;
    private final Map<String, Long> activeUntil = new ConcurrentHashMap<>();

    @Autowired
    public TenantProvisioningStatus(TenantSchemaRegistry registry) {
        this(registry, System::nanoTime);
    }

    TenantProvisioningStatus(TenantSchemaRegistry registry, LongSupplier nanoTime) {
        this.registry = registry;
        this.nanoTime = nanoTime;
    }

    public boolean isProvisioned(String tenantCode) {
        if (tenantCode == null || tenantCode.isBlank()) return false;
        long now = nanoTime.getAsLong();
        Long expiry = activeUntil.get(tenantCode);
        if (expiry != null && now - expiry < 0) return true;

        if (registry.isActive(tenantCode)) {
            activeUntil.put(tenantCode, now + CACHE_TTL.toNanos());
            return true;
        }
        activeUntil.remove(tenantCode);
        return false;
    }
}
