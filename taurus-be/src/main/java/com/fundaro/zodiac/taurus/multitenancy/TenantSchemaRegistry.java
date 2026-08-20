package com.fundaro.zodiac.taurus.multitenancy;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class TenantSchemaRegistry {

    private static final Logger log = LoggerFactory.getLogger(TenantSchemaRegistry.class);

    private final JdbcTemplate jdbcTemplate;

    public TenantSchemaRegistry(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<String> findActiveTenantCodes() {
        try {
            return jdbcTemplate.queryForList(
                "SELECT tenant_code FROM public.tenant_schema_registry WHERE status = 'ACTIVE' ORDER BY tenant_code",
                String.class
            );
        } catch (DataAccessException exception) {
            log.debug("Tenant schema registry is not ready yet", exception);
            return List.of();
        }
    }
}
