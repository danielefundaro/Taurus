package com.fundaro.zodiac.taurus.multitenancy;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class SchemaMultiTenantConnectionProviderTest {

    @Test
    void selectsTenantSchemaAndResetsConnectionBeforeReturningItToThePool() throws Exception {
        DataSource dataSource = mock(DataSource.class);
        Connection connection = mock(Connection.class);
        when(dataSource.getConnection()).thenReturn(connection);
        TenantSchemaNameResolver schemaNameResolver = new TenantSchemaNameResolver();
        SchemaMultiTenantConnectionProvider provider = new SchemaMultiTenantConnectionProvider(dataSource, schemaNameResolver);

        provider.getConnection("acme");
        provider.releaseConnection("acme", connection);

        InOrder calls = inOrder(connection);
        calls.verify(connection).setSchema(schemaNameResolver.resolve("acme"));
        calls.verify(connection).setSchema(TenantSchemaNameResolver.DEFAULT_SCHEMA);
        calls.verify(connection).close();
    }
}
