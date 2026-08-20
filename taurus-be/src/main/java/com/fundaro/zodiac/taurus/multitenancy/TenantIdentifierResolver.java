package com.fundaro.zodiac.taurus.multitenancy;

import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.springframework.stereotype.Component;

@Component
public class TenantIdentifierResolver implements CurrentTenantIdentifierResolver<String> {

    @Override
    public String resolveCurrentTenantIdentifier() {
        return TenantContext.getTenantCode().orElse(TenantSchemaNameResolver.DEFAULT_SCHEMA);
    }

    @Override
    public boolean validateExistingCurrentSessions() {
        return true;
    }
}
