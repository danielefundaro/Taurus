package com.fundaro.zodiac.taurus.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TenantContextTest {

    @Test
    void restoresNestedTenantScopes() {
        assertThat(TenantContext.getTenantCode()).isEmpty();

        try (TenantContext.Scope first = TenantContext.use("tenant-a")) {
            assertThat(TenantContext.getTenantCode()).contains("tenant-a");
            try (TenantContext.Scope second = TenantContext.use("tenant-b")) {
                assertThat(TenantContext.getTenantCode()).contains("tenant-b");
            }
            assertThat(TenantContext.getTenantCode()).contains("tenant-a");
        }

        assertThat(TenantContext.getTenantCode()).isEmpty();
    }
}
