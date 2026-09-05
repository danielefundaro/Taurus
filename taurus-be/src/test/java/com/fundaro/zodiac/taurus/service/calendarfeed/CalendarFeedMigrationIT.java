package com.fundaro.zodiac.taurus.service.calendarfeed;

import static org.assertj.core.api.Assertions.assertThat;
import com.fundaro.zodiac.taurus.IntegrationTest;
import com.fundaro.zodiac.taurus.multitenancy.*;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;

@IntegrationTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestPropertySource(properties = { "application.base-path=D:/data", "spring.liquibase.contexts=test", "spring.security.oauth2.client.registration.oidc.client-id=test", "spring.security.oauth2.client.registration.oidc.client-secret=test" })
class CalendarFeedMigrationIT {
    @MockBean ClientRegistrationRepository clientRegistrationRepository;
    @MockBean JwtDecoder jwtDecoder;
    @Autowired TenantSchemaProvisioningService provisioning;
    @Autowired TenantSchemaNameResolver names;
    @Autowired JdbcTemplate jdbc;
    private final String tenant = "calendar-feed-" + UUID.randomUUID();

    @BeforeAll void provision() { provisioning.provision(tenant); }
    @AfterAll void cleanup() { provisioning.dropSchema(tenant); }

    @Test void provisionsGlobalRegistryAndCompleteTenantSchema() {
        String schema = names.resolve(tenant);
        assertThat(tableExists("public", "calendar_feed_token_registry")).isTrue();
        assertThat(tableExists(schema, "calendar_feed_subscription")).isTrue();
        assertThat(tableExists(schema, "calendar_event_feed_tombstone")).isTrue();
        Integer columns = jdbc.queryForObject("select count(*) from information_schema.columns where table_schema=? and table_name='calendar_event' and column_name in ('calendar_uid','calendar_sequence','calendar_feed_modified_at')", Integer.class, schema);
        assertThat(columns).isEqualTo(3);
    }
    private boolean tableExists(String schema, String table) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from information_schema.tables where table_schema=? and table_name=?)", Boolean.class, schema, table));
    }
}
