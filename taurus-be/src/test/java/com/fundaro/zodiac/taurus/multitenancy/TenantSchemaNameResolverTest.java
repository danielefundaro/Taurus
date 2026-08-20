package com.fundaro.zodiac.taurus.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class TenantSchemaNameResolverTest {

    private final TenantSchemaNameResolver resolver = new TenantSchemaNameResolver();

    @Test
    void generatesStableSafeIdentifiersWithoutEmbeddingTenantInput() {
        String schemaName = resolver.resolve("Acme'; DROP SCHEMA public; --");

        assertThat(schemaName).matches("tenant_[a-f0-9]{32}").hasSize(39);
        assertThat(resolver.resolve("Acme'; DROP SCHEMA public; --")).isEqualTo(schemaName);
        resolver.requireSafeSchemaName(schemaName);
    }

    @Test
    void rejectsIdentifiersNotGeneratedByTheResolver() {
        assertThatThrownBy(() -> resolver.requireSafeSchemaName("tenant_acme;drop"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
