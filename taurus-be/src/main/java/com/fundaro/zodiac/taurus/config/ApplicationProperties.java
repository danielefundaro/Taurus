package com.fundaro.zodiac.taurus.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Properties specific to Taurus.
 * <p>
 * Properties are configured in the {@code application.yml} file.
 * See {@link tech.jhipster.config.JHipsterProperties} for a good example.
 */
@ConfigurationProperties(prefix = "application", ignoreUnknownFields = false)
public class ApplicationProperties {
    private String basePath;
    private OpenSearchProperties openSearch;

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
    }

    public OpenSearchProperties getOpenSearch() {
        return openSearch;
    }

    public void setOpenSearch(OpenSearchProperties openSearch) {
        this.openSearch = openSearch;
    }

    public static class OpenSearchProperties {
        private String host;
        private int port;
        private String schema;
        private String username;
        private String password;

        public String getHost() {
            return host;
        }

        public void setHost(String host) {
            this.host = host;
        }

        public int getPort() {
            return port;
        }

        public void setPort(int port) {
            this.port = port;
        }

        public String getSchema() {
            return schema;
        }

        public void setSchema(String schema) {
            this.schema = schema;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
