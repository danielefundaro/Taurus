package com.fundaro.zodiac.taurus.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Properties specific to Taurus.
 * <p>
 * Properties are configured in the {@code application.yml} file.
 * See {@link tech.jhipster.config.JHipsterProperties} for a good example.
 */
@ConfigurationProperties(prefix = "application", ignoreUnknownFields = false)
public class ApplicationProperties {
    private String basePath;

    private Keycloak keycloak;

    private VapidProperties vapid = new VapidProperties();

    private RetentionProperties retention = new RetentionProperties();

    private CalendarProperties calendar = new CalendarProperties();

    public String getBasePath() {
        return basePath;
    }

    public void setBasePath(String basePath) {
        this.basePath = basePath;
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

    public RetentionProperties getRetention() {
        return retention;
    }

    public void setRetention(RetentionProperties retention) {
        this.retention = retention;
    }

    public CalendarProperties getCalendar() {
        return calendar;
    }

    public void setCalendar(CalendarProperties calendar) {
        this.calendar = calendar;
    }

    private TesseractProperties tesseract = new TesseractProperties();

    public TesseractProperties getTesseract() {
        return tesseract;
    }

    public void setTesseract(TesseractProperties tesseract) {
        this.tesseract = tesseract;
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

    public static class TesseractProperties {
        private boolean enabled = false;
        private String dataPath = "C:\\Program Files\\Tesseract-OCR\\tessdata";
        private String language = "ita+eng";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public String getDataPath() { return dataPath; }
        public void setDataPath(String dataPath) { this.dataPath = dataPath; }

        public String getLanguage() { return language; }
        public void setLanguage(String language) { this.language = language; }
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

    public static class RetentionProperties {
        private boolean cleanupEnabled = true;
        private String cleanupCron = "0 0 3 * * *";
        private int noticesDays = 365;
        private int lastResearchDays = 365;
        private int sentPushRemindersDays = 30;
        private List<String> tenantIndices = List.of(
            "albums",
            "instruments",
            "media",
            "queue-upload-files",
            "tracks",
            "users",
            "calendar-events"
        );

        public boolean isCleanupEnabled() { return cleanupEnabled; }
        public void setCleanupEnabled(boolean cleanupEnabled) { this.cleanupEnabled = cleanupEnabled; }

        public String getCleanupCron() { return cleanupCron; }
        public void setCleanupCron(String cleanupCron) { this.cleanupCron = cleanupCron; }

        public int getNoticesDays() { return noticesDays; }
        public void setNoticesDays(int noticesDays) { this.noticesDays = noticesDays; }

        public int getLastResearchDays() { return lastResearchDays; }
        public void setLastResearchDays(int lastResearchDays) { this.lastResearchDays = lastResearchDays; }

        public int getSentPushRemindersDays() { return sentPushRemindersDays; }
        public void setSentPushRemindersDays(int sentPushRemindersDays) { this.sentPushRemindersDays = sentPushRemindersDays; }

        public List<String> getTenantIndices() { return tenantIndices; }
        public void setTenantIndices(List<String> tenantIndices) { this.tenantIndices = tenantIndices; }
    }

    public static class CalendarProperties {
        private RecurrenceProperties recurrence = new RecurrenceProperties();

        public RecurrenceProperties getRecurrence() {
            return recurrence;
        }

        public void setRecurrence(RecurrenceProperties recurrence) {
            this.recurrence = recurrence;
        }
    }

    public static class RecurrenceProperties {
        private int maxOccurrences = 500;

        public int getMaxOccurrences() {
            return maxOccurrences;
        }

        public void setMaxOccurrences(int maxOccurrences) {
            this.maxOccurrences = maxOccurrences;
        }
    }
}
