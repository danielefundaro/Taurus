package com.fundaro.zodiac.taurus.config;

import com.fundaro.zodiac.taurus.config.changelog.service.ChangelogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import static com.fundaro.zodiac.taurus.config.Constants.MASTER_CHANGELOG_FILE_PATH;

@Configuration
public class OpenSearchConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(OpenSearchConfiguration.class);

    private final Environment env;

    private final ChangelogService changelogService;

    public OpenSearchConfiguration(Environment env, ChangelogService changelogService) {
        this.env = env;
        this.changelogService = changelogService;
    }

    @Bean
    public Boolean opensearch() throws IOException, NoSuchAlgorithmException {
        LOG.debug("Starting OpenSearch Liquibase asynchronously, your indices might not be ready at startup!");
        long startTime = System.currentTimeMillis();

        changelogService.createChangeLogIndex();
        changelogService.extractAllResources(MASTER_CHANGELOG_FILE_PATH, null);

        LOG.debug("OpenSearch Liquibase has updated your indices in {} ms", System.currentTimeMillis() - startTime);
        return null;
    }
}
