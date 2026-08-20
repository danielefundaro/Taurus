package com.fundaro.zodiac.taurus.multitenancy;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.hibernate.service.UnknownUnwrapTypeException;
import org.springframework.stereotype.Component;

@Component
public class SchemaMultiTenantConnectionProvider implements MultiTenantConnectionProvider<String> {

    private final DataSource dataSource;
    private final TenantSchemaNameResolver schemaNameResolver;

    public SchemaMultiTenantConnectionProvider(DataSource dataSource, TenantSchemaNameResolver schemaNameResolver) {
        this.dataSource = dataSource;
        this.schemaNameResolver = schemaNameResolver;
    }

    @Override
    public Connection getAnyConnection() throws SQLException {
        Connection connection = dataSource.getConnection();
        connection.setSchema(TenantSchemaNameResolver.DEFAULT_SCHEMA);
        return connection;
    }

    @Override
    public void releaseAnyConnection(Connection connection) throws SQLException {
        release(connection);
    }

    @Override
    public Connection getConnection(String tenantIdentifier) throws SQLException {
        Connection connection = dataSource.getConnection();
        String schemaName = TenantSchemaNameResolver.DEFAULT_SCHEMA.equals(tenantIdentifier)
            ? TenantSchemaNameResolver.DEFAULT_SCHEMA
            : schemaNameResolver.resolve(tenantIdentifier);
        try {
            connection.setSchema(schemaName);
            return connection;
        } catch (SQLException exception) {
            connection.close();
            throw exception;
        }
    }

    @Override
    public void releaseConnection(String tenantIdentifier, Connection connection) throws SQLException {
        release(connection);
    }

    private void release(Connection connection) throws SQLException {
        try {
            connection.setSchema(TenantSchemaNameResolver.DEFAULT_SCHEMA);
        } finally {
            connection.close();
        }
    }

    @Override
    public boolean supportsAggressiveRelease() {
        return false;
    }

    @Override
    public boolean isUnwrappableAs(Class<?> unwrapType) {
        return unwrapType.isAssignableFrom(getClass()) || unwrapType.isAssignableFrom(MultiTenantConnectionProvider.class);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T unwrap(Class<T> unwrapType) {
        if (isUnwrappableAs(unwrapType)) {
            return (T) this;
        }
        throw new UnknownUnwrapTypeException(unwrapType);
    }
}
