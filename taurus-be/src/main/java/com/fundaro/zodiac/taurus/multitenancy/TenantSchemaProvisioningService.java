package com.fundaro.zodiac.taurus.multitenancy;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseProperties;
import org.springframework.stereotype.Service;

@Service
public class TenantSchemaProvisioningService {

    private static final Logger log = LoggerFactory.getLogger(TenantSchemaProvisioningService.class);
    private static final String TENANT_CHANGELOG = "classpath:config/liquibase/tenant-master.xml";
    private static final List<String> TENANT_TABLES = List.of(
        "last_research",
        "notices",
        "preferences",
        "push_subscriptions",
        "push_reminders",
        "user_legal_acceptance",
        "inventory_item",
        "inventory_item_photo",
        "inventory_assignment",
        "inventory_return",
        "inventory_return_photo",
        "inventory_search_outbox",
        "inventory_erasure_request",
        "inventory_report_export"
    );
    private static final List<String> CHILD_TABLES = List.of(
        "inventory_assignment_revision",
        "inventory_assignment_decision"
    );

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
                migrateLegacyData(tenantCode, schemaName);
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

    private void migrateLegacyData(String tenantCode, String schemaName) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                for (String tableName : TENANT_TABLES) {
                    copyTenantTable(connection, tenantCode, schemaName, tableName);
                }
                copyChildTable(
                    connection,
                    tenantCode,
                    schemaName,
                    "inventory_assignment_revision",
                    "JOIN public.inventory_assignment parent ON parent.id = source.assignment_id"
                );
                copyChildTable(
                    connection,
                    tenantCode,
                    schemaName,
                    "inventory_assignment_decision",
                    "JOIN public.inventory_assignment_revision revision ON revision.id = source.revision_id " +
                    "JOIN public.inventory_assignment parent ON parent.id = revision.assignment_id"
                );
                for (String tableName : TENANT_TABLES) {
                    synchronizeSequence(connection, schemaName, tableName);
                }
                for (String tableName : CHILD_TABLES) {
                    synchronizeSequence(connection, schemaName, tableName);
                }
                connection.commit();
            } catch (SQLException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private void copyTenantTable(Connection connection, String tenantCode, String schemaName, String tableName) throws SQLException {
        if (!tableExists(connection, "public", tableName) || !tableExists(connection, schemaName, tableName)) {
            return;
        }
        String columns = commonColumns(connection, schemaName, tableName);
        if (columns.isBlank()) {
            return;
        }
        String sql = "INSERT INTO " + qualified(schemaName, tableName) + " (" + columns + ") SELECT " + columns +
            " FROM " + qualified("public", tableName) + " WHERE tenant_code = ? ON CONFLICT DO NOTHING";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tenantCode);
            statement.executeUpdate();
        }
    }

    private void copyChildTable(
        Connection connection,
        String tenantCode,
        String schemaName,
        String tableName,
        String joins
    ) throws SQLException {
        if (!tableExists(connection, "public", tableName) || !tableExists(connection, schemaName, tableName)) {
            return;
        }
        String columns = commonColumns(connection, schemaName, tableName);
        if (columns.isBlank()) {
            return;
        }
        String sourceColumns = "source." + columns.replace(",", ",source.");
        String sql = "INSERT INTO " + qualified(schemaName, tableName) + " (" + columns + ") SELECT " + sourceColumns +
            " FROM " + qualified("public", tableName) + " source " + joins +
            " WHERE parent.tenant_code = ? ON CONFLICT DO NOTHING";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, tenantCode);
            statement.executeUpdate();
        }
    }

    private String commonColumns(Connection connection, String targetSchema, String tableName) throws SQLException {
        String sql = """
            SELECT string_agg(quote_ident(target.column_name), ',' ORDER BY target.ordinal_position)
            FROM information_schema.columns target
            JOIN information_schema.columns source
              ON source.table_schema = 'public'
             AND source.table_name = target.table_name
             AND source.column_name = target.column_name
            WHERE target.table_schema = ?
              AND target.table_name = ?
              AND target.column_name <> 'tenant_code'
            """;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, targetSchema);
            statement.setString(2, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                String columns = resultSet.getString(1);
                return columns == null ? "" : columns;
            }
        }
    }

    private void synchronizeSequence(Connection connection, String schemaName, String tableName) throws SQLException {
        if (!tableExists(connection, schemaName, tableName)) {
            return;
        }
        String relation = quoteIdentifier(schemaName) + "." + quoteIdentifier(tableName);
        String sql = "SELECT setval(pg_get_serial_sequence(?, 'id'), COALESCE(MAX(id), 1), COUNT(*) > 0) FROM " + relation;
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, relation);
            statement.execute();
        }
    }

    private boolean tableExists(Connection connection, String schemaName, String tableName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
            "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = ? AND table_name = ?)"
        )) {
            statement.setString(1, schemaName);
            statement.setString(2, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getBoolean(1);
            }
        }
    }

    private void ensureRegistry(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("""
                CREATE TABLE IF NOT EXISTS public.tenant_schema_registry (
                    tenant_code varchar(255) PRIMARY KEY,
                    schema_name varchar(63) UNIQUE NOT NULL,
                    status varchar(32) NOT NULL,
                    last_error varchar(2000),
                    created_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at timestamp with time zone NOT NULL DEFAULT CURRENT_TIMESTAMP
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
