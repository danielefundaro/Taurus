package com.fundaro.zodiac.taurus.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundaro.zodiac.taurus.config.changelog.bean.ChangelogFile;
import com.fundaro.zodiac.taurus.config.changelog.service.ChangelogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
                    ChangelogFile mapIndex = getMaps(filename, typeReference);

                    // If the file exists, check and add index
                    if (mapIndex != null) {
                        switch (mapIndex.getAction()) {
                            case CREATE_INDEX -> changelogService.createIndex(mapIndex, filename);
                            case LOAD_DATA ->
                                changelogService.loadData(mapIndex, filename, (resourceName, dataTypeReference) -> {
                                    try {
                                        return getMaps(resourceName, dataTypeReference);
                                    } catch (IOException e) {
                                        throw new RuntimeException(e);
                                    }
                                });
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
        return getMaps("/config/opensearch/master.json", typeReference);
    }

    private <T> T getMaps(String resourceName, TypeReference<T> typeReference) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream inputStream = this.getClass().getResourceAsStream(resourceName)) {
            if (inputStream != null) {
                String json = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));
                return mapper.readValue(json, typeReference);
            }
        }

        return null;
    }
}
