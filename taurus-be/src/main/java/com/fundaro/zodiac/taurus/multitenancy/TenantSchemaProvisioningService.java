package com.fundaro.zodiac.taurus.multitenancy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class TenantSchemaProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(TenantSchemaProvisioningService.class);
    private static final String TENANT_CHANGELOG = "classpath:config/liquibase/tenant-master.xml";
    private final DataSource dataSource;
    private final LiquibaseProperties liquibaseProperties;
    private final TenantSchemaNameResolver schemaNameResolver;

    public TenantSchemaProvisioningService(
        DataSource dataSource,
        LiquibaseProperties liquibaseProperties,
        TenantSchemaNameResolver schemaNameResolver
    ) {
        this.dataSource = dataSource;
        this.liquibaseProperties = liquibaseProperties;
        this.schemaNameResolver = schemaNameResolver;
    }

    public void provision(String tenantCode) {
        if (tenantCode == null || tenantCode.isBlank()) {
            throw new IllegalArgumentException("Tenant code is required");
        }
        String schemaName = schemaNameResolver.resolve(tenantCode);
        schemaNameResolver.requireSafeSchemaName(schemaName);

        try (Connection lockConnection = dataSource.getConnection()) {
            lockConnection.setAutoCommit(true);
            ensureRegistry(lockConnection);
            acquireLock(lockConnection, tenantCode);
            try {
                markProvisioning(lockConnection, tenantCode, schemaName);
                createSchema(lockConnection, schemaName);
                migrateSchema(schemaName, tenantCode);
                markActive(lockConnection, tenantCode, schemaName);
                log.info("Tenant {} PostgreSQL schema {} is ready", tenantCode, schemaName);
            } catch (Exception exception) {
                markFailed(lockConnection, tenantCode, schemaName, exception);
                throw new TenantSchemaProvisioningException("Could not provision PostgreSQL schema for tenant " + tenantCode, exception);
            } finally {
                releaseLock(lockConnection, tenantCode);
            }
        } catch (SQLException exception) {
            throw new TenantSchemaProvisioningException("Could not provision PostgreSQL schema for tenant " + tenantCode, exception);
        }
    }

    public void migrateExistingSchema(String tenantCode) {
        if (tenantCode == null || tenantCode.isBlank()) {
            throw new IllegalArgumentException("Tenant code is required");
        }
        String schemaName = schemaNameResolver.resolve(tenantCode);
        schemaNameResolver.requireSafeSchemaName(schemaName);

        try (Connection lockConnection = dataSource.getConnection()) {
            lockConnection.setAutoCommit(true);
            ensureRegistry(lockConnection);
            acquireLock(lockConnection, tenantCode);
            try {
                createSchema(lockConnection, schemaName);
                migrateSchema(schemaName, tenantCode);
                markActive(lockConnection, tenantCode, schemaName);
                log.info("Tenant {} PostgreSQL schema {} is up to date", tenantCode, schemaName);
            } catch (Exception exception) {
                recordMigrationError(lockConnection, tenantCode, exception);
                throw new TenantSchemaProvisioningException("Could not migrate PostgreSQL schema for tenant " + tenantCode, exception);
            } finally {
                releaseLock(lockConnection, tenantCode);
            }
        } catch (SQLException exception) {
            throw new TenantSchemaProvisioningException("Could not migrate PostgreSQL schema for tenant " + tenantCode, exception);
        }
    }

    public void linkTenant(Long tenantId, String tenantCode) {
        if (tenantId == null) throw new IllegalArgumentException("Tenant id is required");
        new JdbcTemplate(dataSource).update(
            "UPDATE public.tenant_schema_registry SET tenant_id = ?, updated_at = CURRENT_TIMESTAMP WHERE tenant_code = ?",
            tenantId,
            tenantCode
        );
    }

    public void dropSchema(String tenantCode) {
        String schemaName = schemaNameResolver.resolve(tenantCode);
        schemaNameResolver.requireSafeSchemaName(schemaName);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(true);
            ensureRegistry(connection);
            acquireLock(connection, tenantCode);
            try (Statement statement = connection.createStatement()) {
                statement.execute("DROP SCHEMA IF EXISTS " + quoteIdentifier(schemaName) + " CASCADE");
                try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE public.tenant_schema_registry SET status = 'DELETED', updated_at = CURRENT_TIMESTAMP WHERE tenant_code = ?"
                )) {
                    update.setString(1, tenantCode);
                    update.executeUpdate();
                }
            } finally {
                releaseLock(connection, tenantCode);
            }
        } catch (SQLException exception) {
            throw new TenantSchemaProvisioningException("Could not drop PostgreSQL schema for tenant " + tenantCode, exception);
        }
    }

    private void migrateSchema(String schemaName, String tenantCode) throws Exception {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog(TENANT_CHANGELOG);
        liquibase.setDefaultSchema(schemaName);
        liquibase.setLiquibaseSchema(schemaName);
        liquibase.setContexts(liquibaseProperties.getContexts());
        liquibase.setLabelFilter(liquibaseProperties.getLabelFilter());
        Map<String, String> parameters = liquibaseProperties.getParameters() == null
            ? new HashMap<>()
            : new HashMap<>(liquibaseProperties.getParameters());
        parameters.put("tenantCode", tenantCode);
        liquibase.setChangeLogParameters(parameters);
        liquibase.setShouldRun(true);
        liquibase.afterPropertiesSet();
    }

    private void ensureRegistry(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS public.tenant_schema_registry (
                    tenant_code varchar(255) PRIMARY KEY,
                    tenant_id bigint UNIQUE REFERENCES public.tenant(id) ON DELETE CASCADE,
                    schema_name varchar(63) UNIQUE NOT NULL,
                    status varchar(32) NOT NULL,
                    last_error varchar(2000),
                    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    deleted boolean NOT NULL DEFAULT false,
                    insert_by varchar(255) NOT NULL DEFAULT 'system',
                    insert_date timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    edit_by varchar(255) NOT NULL DEFAULT 'system',
                    edit_date timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
                )
                """);
        }
    }

    private void markProvisioning(Connection connection, String tenantCode, String schemaName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO public.tenant_schema_registry(tenant_code, schema_name, status, last_error)
            VALUES (?, ?, 'PROVISIONING', NULL)
            ON CONFLICT (tenant_code) DO UPDATE
            SET schema_name = EXCLUDED.schema_name, status = 'PROVISIONING', last_error = NULL, updated_at = CURRENT_TIMESTAMP
            """)) {
            statement.setString(1, tenantCode);
            statement.setString(2, schemaName);
            statement.executeUpdate();
        }
    }

    private void markActive(Connection connection, String tenantCode, String schemaName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
            UPDATE public.tenant_schema_registry
            SET schema_name = ?, status = 'ACTIVE', last_error = NULL, updated_at = CURRENT_TIMESTAMP
            WHERE tenant_code = ?
            """)) {
            statement.setString(1, schemaName);
            statement.setString(2, tenantCode);
            statement.executeUpdate();
        }
    }

    private void markFailed(Connection connection, String tenantCode, String schemaName, Exception exception) {
        try (PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO public.tenant_schema_registry(tenant_code, schema_name, status, last_error)
            VALUES (?, ?, 'FAILED', ?)
            ON CONFLICT (tenant_code) DO UPDATE
            SET status = 'FAILED', last_error = EXCLUDED.last_error, updated_at = CURRENT_TIMESTAMP
            """)) {
            statement.setString(1, tenantCode);
            statement.setString(2, schemaName);
            statement.setString(3, abbreviate(exception.getMessage()));
            statement.executeUpdate();
        } catch (SQLException registryException) {
            exception.addSuppressed(registryException);
        }
    }

    private void recordMigrationError(Connection connection, String tenantCode, Exception exception) {
        try (PreparedStatement statement = connection.prepareStatement("""
            UPDATE public.tenant_schema_registry
            SET last_error = ?, updated_at = CURRENT_TIMESTAMP
            WHERE tenant_code = ?
            """)) {
            statement.setString(1, abbreviate(exception.getMessage()));
            statement.setString(2, tenantCode);
            statement.executeUpdate();
        } catch (SQLException registryException) {
            exception.addSuppressed(registryException);
        }
    }

    private void createSchema(Connection connection, String schemaName) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA IF NOT EXISTS " + quoteIdentifier(schemaName));
        }
    }

    private void acquireLock(Connection connection, String tenantCode) throws SQLException {
        executeLockFunction(connection, "SELECT pg_advisory_lock(hashtextextended(?, 0))", tenantCode);
    }

    private void releaseLock(Connection connection, String tenantCode) throws SQLException {
        executeLockFunction(connection, "SELECT pg_advisory_unlock(hashtextextended(?, 0))", tenantCode);
    }

    private void executeLockFunction(Connection connection, String sql, String tenantCode) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, "taurus-tenant-schema:" + tenantCode);
            statement.execute();
        }
    }

    private String qualified(String schemaName, String tableName) {
        return quoteIdentifier(schemaName) + "." + quoteIdentifier(tableName);
    }

    private String quoteIdentifier(String identifier) {
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }

    private String abbreviate(String value) {
        if (value == null) {
            return "Unknown provisioning error";
        }
        return value.length() <= 2000 ? value : value.substring(0, 2000);
    }
}
