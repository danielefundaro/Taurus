package com.fundaro.zodiac.taurus.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fundaro.zodiac.taurus.config.changelog.bean.ChangelogFile;
import com.fundaro.zodiac.taurus.config.changelog.service.ChangelogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashSet;
import java.util.Map;

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
        LinkedHashSet<Map<String, String>> master = getMasterJson();

        if (master != null && !master.isEmpty()) {
            for (Map<String, String> change : master) {
                if (change.containsKey("file")) {
                    String filename = change.get("file");
                    TypeReference<ChangelogFile> typeReference = new TypeReference<>() {
                    };
                    ChangelogFile mapIndex = changelogService.getMaps(filename, typeReference);

                    // If the file exists, check and add index
                    if (mapIndex != null) {
                        switch (mapIndex.getAction()) {
                            case CREATE_INDEX -> changelogService.createIndex(mapIndex, filename);
                            case LOAD_DATA -> changelogService.loadData(mapIndex, filename);
                        }
                    } else {
                        LOG.warn("Skip file {} because does not exists", filename);
                    }
                }
            }
        }

        LOG.debug("OpenSearch Liquibase has updated your indices in {} ms", System.currentTimeMillis() - startTime);
        return null;
    }

    private LinkedHashSet<Map<String, String>> getMasterJson() throws IOException {
        TypeReference<LinkedHashSet<Map<String, String>>> typeReference = new TypeReference<>() {
        };
        return changelogService.getMaps("/config/opensearch/master.json", typeReference);
    }
}
