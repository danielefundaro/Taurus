package com.fundaro.zodiac.taurus.config;

import com.fundaro.zodiac.taurus.multitenancy.SchemaMultiTenantConnectionProvider;
import com.fundaro.zodiac.taurus.multitenancy.TenantIdentifierResolver;
import java.util.Map;
import org.hibernate.cfg.MultiTenancySettings;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Configuration;

@Configuration
public class HibernateMultiTenancyConfiguration implements HibernatePropertiesCustomizer {

    private final SchemaMultiTenantConnectionProvider connectionProvider;
    private final TenantIdentifierResolver tenantIdentifierResolver;

    public HibernateMultiTenancyConfiguration(
        SchemaMultiTenantConnectionProvider connectionProvider,
        TenantIdentifierResolver tenantIdentifierResolver
    ) {
        this.connectionProvider = connectionProvider;
        this.tenantIdentifierResolver = tenantIdentifierResolver;
    }

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        hibernateProperties.put(MultiTenancySettings.MULTI_TENANT_CONNECTION_PROVIDER, connectionProvider);
        hibernateProperties.put(MultiTenancySettings.MULTI_TENANT_IDENTIFIER_RESOLVER, tenantIdentifierResolver);
    }
}
