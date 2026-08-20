package com.fundaro.zodiac.taurus.multitenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseProperties;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.DockerClientFactory;

class TenantSchemaProvisioningServiceIT {

    private static DataSource dataSource;
    private static TenantSchemaNameResolver schemaNameResolver;
    private static TenantSchemaProvisioningService provisioningService;
    private static PostgreSQLContainer<?> postgres;

    @BeforeAll
    static void setUpDatabase() throws Exception {
        String jdbcUrl = System.getProperty("taurus.test.postgres.url");
        String username = System.getProperty("taurus.test.postgres.username", "taurus");
        String password = System.getProperty("taurus.test.postgres.password", "password");
        if (jdbcUrl == null) {
            assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is required for the PostgreSQL integration test");
            postgres = new PostgreSQLContainer<>("postgres:17.0");
            postgres.start();
            jdbcUrl = postgres.getJdbcUrl();
            username = postgres.getUsername();
            password = postgres.getPassword();
        }

        PGSimpleDataSource postgresDataSource = new PGSimpleDataSource();
        postgresDataSource.setUrl(jdbcUrl);
        postgresDataSource.setUser(username);
        postgresDataSource.setPassword(password);
        dataSource = postgresDataSource;

        SpringLiquibase globalLiquibase = new SpringLiquibase();
        globalLiquibase.setDataSource(dataSource);
        globalLiquibase.setChangeLog("classpath:config/liquibase/master.xml");
        globalLiquibase.setDefaultSchema("public");
        globalLiquibase.setLiquibaseSchema("public");
        globalLiquibase.afterPropertiesSet();

        schemaNameResolver = new TenantSchemaNameResolver();
        provisioningService = new TenantSchemaProvisioningService(dataSource, new LiquibaseProperties(), schemaNameResolver);
    }

    @AfterAll
    static void stopDatabase() {
        if (postgres != null) {
            postgres.stop();
        }
    }

    @Test
    void provisionsIdempotentlyAndCopiesOnlyTheSelectedLegacyTenant() throws Exception {
        String tenantCode = "Acme'; DROP SCHEMA public; --";
        String otherTenant = "other";
        insertLegacyPreference(tenantCode, "selected");
        insertLegacyPreference(otherTenant, "not-selected");

        provisioningService.provision(tenantCode);
        provisioningService.provision(tenantCode);

        String schemaName = schemaNameResolver.resolve(tenantCode);
        assertThat(queryBoolean("SELECT EXISTS (SELECT 1 FROM information_schema.schemata WHERE schema_name = ?)", schemaName)).isTrue();
        assertThat(queryLong("SELECT COUNT(*) FROM " + quote(schemaName) + ".preferences")).isEqualTo(1);
        assertThat(queryString("SELECT status FROM public.tenant_schema_registry WHERE tenant_code = ?", tenantCode)).isEqualTo("ACTIVE");
        assertThat(queryBoolean("SELECT to_regclass(?) IS NOT NULL", schemaName + ".inventory_item")).isTrue();
        assertThat(queryBoolean(
            "SELECT EXISTS (SELECT 1 FROM information_schema.columns WHERE table_schema = ? AND column_name = 'tenant_code')",
            schemaName
        )).isFalse();
    }

    private static void insertLegacyPreference(String tenantCode, String value) throws Exception {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(
            "INSERT INTO public.preferences(deleted, key, value, user_id, tenant_code) VALUES (false, 'test', ?, 'user', ?)"
        )) {
            statement.setString(1, value);
            statement.setString(2, tenantCode);
            statement.executeUpdate();
        }
    }

    private static boolean queryBoolean(String sql, String parameter) throws Exception {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, parameter);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getBoolean(1);
            }
        }
    }

    private static long queryLong(String sql) throws Exception {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }

    private static String queryString(String sql, String parameter) throws Exception {
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, parameter);
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getString(1);
            }
        }
    }

    private static String quote(String identifier) {
        return '"' + identifier.replace("\"", "\"\"") + '"';
    }
}
