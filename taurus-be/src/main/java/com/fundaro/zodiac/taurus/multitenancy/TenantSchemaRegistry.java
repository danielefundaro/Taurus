package com.fundaro.zodiac.taurus.multitenancy;

import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class TenantSchemaRegistry {

    private static final Logger log = LoggerFactory.getLogger(TenantSchemaRegistry.class);
    private static final String FINANCE_ENABLED_TENANTS_QUERY = """
        SELECT registry.tenant_code
        FROM public.tenant_schema_registry registry
        JOIN public.tenant tenant ON tenant.id = registry.tenant_id
        WHERE registry.status = 'ACTIVE'
          AND registry.deleted = FALSE
          AND tenant.deleted = FALSE
          AND tenant.active = TRUE
          AND tenant.finance_enabled = TRUE
        ORDER BY registry.tenant_code
        """;
    private static final String INVENTORY_ENABLED_TENANTS_QUERY = """
        SELECT registry.tenant_code
        FROM public.tenant_schema_registry registry
        JOIN public.tenant tenant ON tenant.id = registry.tenant_id
        WHERE registry.status = 'ACTIVE'
          AND registry.deleted = FALSE
          AND tenant.deleted = FALSE
          AND tenant.active = TRUE
          AND tenant.inventory_enabled = TRUE
        ORDER BY registry.tenant_code
        """;

    private final JdbcTemplate jdbcTemplate;

    public TenantSchemaRegistry(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<String> findActiveTenantCodes() {
        try {
            return queryActiveTenantCodes();
        } catch (DataAccessException exception) {
            log.debug("Tenant schema registry is not ready yet", exception);
            return List.of();
        }
    }

    public Optional<String> findActiveTenantCode(Long tenantId) {
        return jdbcTemplate.query(
            "SELECT tenant_code FROM public.tenant_schema_registry WHERE tenant_id = ? AND status = 'ACTIVE' AND deleted = FALSE",
            rs -> rs.next() ? Optional.of(rs.getString(1)) : Optional.empty(), tenantId
        );
    }

    public List<String> requireActiveTenantCodes() {
        return queryActiveTenantCodes();
    }

    public List<String> findFinanceEnabledTenantCodes() {
        return queryFeatureEnabledTenantCodes(FINANCE_ENABLED_TENANTS_QUERY);
    }

    public List<String> findInventoryEnabledTenantCodes() {
        return queryFeatureEnabledTenantCodes(INVENTORY_ENABLED_TENANTS_QUERY);
    }

    private List<String> queryActiveTenantCodes() {
        return jdbcTemplate.queryForList(
            "SELECT tenant_code FROM public.tenant_schema_registry WHERE status = 'ACTIVE' AND deleted = FALSE ORDER BY tenant_code",
            String.class
        );
    }

    private List<String> queryFeatureEnabledTenantCodes(String query) {
        try {
            return jdbcTemplate.queryForList(query, String.class);
        } catch (DataAccessException exception) {
            log.debug("Tenant feature flags are not ready yet", exception);
            return List.of();
        }
    }
}
