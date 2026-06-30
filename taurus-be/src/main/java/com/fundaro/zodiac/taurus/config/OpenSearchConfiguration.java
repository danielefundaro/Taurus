package com.fundaro.zodiac.taurus.config;

import com.fundaro.zodiac.taurus.config.changelog.service.ChangelogService;
import com.fundaro.zodiac.taurus.service.OpenSearchService;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.Map;

import static com.fundaro.zodiac.taurus.config.Constants.MASTER_CHANGELOG_FILE_PATH;
import static com.fundaro.zodiac.taurus.config.Constants.TENANT_CHANGELOG_FILE_PATH;

@Configuration
public class OpenSearchConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(OpenSearchConfiguration.class);

    private final Environment env;
    private final ChangelogService changelogService;
    private final OpenSearchService openSearchService;

    public OpenSearchConfiguration(Environment env, ChangelogService changelogService, OpenSearchService openSearchService) {
        this.env = env;
        this.changelogService = changelogService;
        this.openSearchService = openSearchService;
    }

    @Bean
    public Boolean opensearch() throws IOException, NoSuchAlgorithmException {
        LOG.debug("Starting OpenSearch Liquibase asynchronously, your indices might not be ready at startup!");
        long startTime = System.currentTimeMillis();

        changelogService.createChangeLogIndex();
        changelogService.extractAllResources(MASTER_CHANGELOG_FILE_PATH, null);
        applyTenantChangelogToExistingTenants();

        LOG.debug("OpenSearch Liquibase has updated your indices in {} ms", System.currentTimeMillis() - startTime);
        return null;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void applyTenantChangelogToExistingTenants() {
        try {
            SearchResponse<Map> response = openSearchService.search(request -> request
                .index("tenants")
                .size(10000)
                .query(q -> q.bool(b -> b
                    .must(m -> m.exists(e -> e.field("deleted")))
                    .must(m -> m.match(ma -> ma.field("deleted").query(v -> v.booleanValue(false))))
                )), Map.class);

            if (response == null || response.hits() == null) {
                return;
            }

            for (var hit : response.hits().hits()) {
                Map<String, Object> source = hit.source();
                if (source != null && source.get("code") instanceof String tenantCode) {
                    LOG.debug("Applying tenant changelog to existing tenant: {}", tenantCode);
                    changelogService.extractAllResources(TENANT_CHANGELOG_FILE_PATH, tenantCode);
                }
            }
        } catch (Exception e) {
            LOG.warn("Could not apply tenant changelog to existing tenants: {}", e.getMessage());
        }
    }
}
