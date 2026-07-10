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

    private Keycloak keycloak;

    private VapidProperties vapid = new VapidProperties();

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

    public Keycloak getKeycloak() {
        return keycloak;
    }

    public void setKeycloak(Keycloak keycloak) {
        this.keycloak = keycloak;
    }

    public VapidProperties getVapid() {
        return vapid;
    }

    public void setVapid(VapidProperties vapid) {
        this.vapid = vapid;
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

    public static class Keycloak {
        private String masterUri;

        private final Admin admin = new Admin();

        public String getMasterUri() {
            return masterUri;
        }

        public void setMasterUri(String masterUri) {
            this.masterUri = masterUri;
        }

        public Admin getAdmin() {
            return admin;
        }

        public static class Admin {
            private String issuerUri;

            private String username;

            private String password;

            public String getIssuerUri() {
                return issuerUri;
            }

            public void setIssuerUri(String issuerUri) {
                this.issuerUri = issuerUri;
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

    public static class VapidProperties {
        private String publicKey = "CHANGE_ME";
        private String privateKey = "CHANGE_ME";
        private String subject = "mailto:admin@taurus.it";

        public String getPublicKey() { return publicKey; }
        public void setPublicKey(String publicKey) { this.publicKey = publicKey; }

        public String getPrivateKey() { return privateKey; }
        public void setPrivateKey(String privateKey) { this.privateKey = privateKey; }

        public String getSubject() { return subject; }
        public void setSubject(String subject) { this.subject = subject; }
    }
}
