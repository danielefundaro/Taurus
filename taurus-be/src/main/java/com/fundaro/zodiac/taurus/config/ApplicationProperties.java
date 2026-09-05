package com.fundaro.zodiac.taurus.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import org.springframework.util.unit.DataSize;

import java.util.List;

/**
 * Properties specific to Taurus.
 * <p>
 * Properties are configured in the {@code application.yml} file.
 * See {@link tech.jhipster.config.JHipsterProperties} for a good example.
 */
@ConfigurationProperties(prefix = "application", ignoreUnknownFields = false)
@Validated
public class ApplicationProperties {
    private String basePath;

    private Keycloak keycloak;

    private VapidProperties vapid = new VapidProperties();

    private RetentionProperties retention = new RetentionProperties();

    private MediaProperties media = new MediaProperties();

    private CalendarProperties calendar = new CalendarProperties();

    @Valid
    private CalendarFeedProperties calendarFeed = new CalendarFeedProperties();

    private NotificationProperties notifications = new NotificationProperties();

    @Valid
    private NotificationPreferencesProperties notificationPreferences = new NotificationPreferencesProperties();

    @Valid
    private NotificationPushDeliveryProperties notificationPushDelivery = new NotificationPushDeliveryProperties();

    @Valid
    private DashboardProperties dashboard = new DashboardProperties();

    @Valid
    private OnboardingProperties onboarding = new OnboardingProperties();

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

    public MediaProperties getMedia() {
        return media;
    }

    public void setMedia(MediaProperties media) {
        this.media = media;
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

    public CalendarFeedProperties getCalendarFeed() { return calendarFeed; }
    public void setCalendarFeed(CalendarFeedProperties value) { calendarFeed = value; }

    public NotificationProperties getNotifications() { return notifications; }

    public void setNotifications(NotificationProperties notifications) { this.notifications = notifications; }

    public NotificationPreferencesProperties getNotificationPreferences() { return notificationPreferences; }

    public void setNotificationPreferences(NotificationPreferencesProperties value) { notificationPreferences = value; }

    public NotificationPushDeliveryProperties getNotificationPushDelivery() { return notificationPushDelivery; }

    public void setNotificationPushDelivery(NotificationPushDeliveryProperties value) { notificationPushDelivery = value; }

    public DashboardProperties getDashboard() { return dashboard; }

    public void setDashboard(DashboardProperties dashboard) { this.dashboard = dashboard; }

    public OnboardingProperties getOnboarding() { return onboarding; }

    public void setOnboarding(OnboardingProperties onboarding) { this.onboarding = onboarding; }

    private TesseractProperties tesseract = new TesseractProperties();

    public TesseractProperties getTesseract() {
        return tesseract;
    }

    public void setTesseract(TesseractProperties tesseract) {
        this.tesseract = tesseract;
    }

    public static class Keycloak {
        private String masterUri;

        private boolean provisionRoles;

        private final Admin admin = new Admin();

        public String getMasterUri() {
            return masterUri;
        }

        public void setMasterUri(String masterUri) {
            this.masterUri = masterUri;
        }

        public boolean isProvisionRoles() {
            return provisionRoles;
        }

        public void setProvisionRoles(boolean provisionRoles) {
            this.provisionRoles = provisionRoles;
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

    /** Pulizia periodica dei residui nello storage dei tenant. */
    public static class MediaProperties {
        private boolean cleanupEnabled = true;
        private String cleanupCron = "0 30 3 * * *";
        private int temporaryFileHours = 24;
        private int orphanFileHours = 168;

        public boolean isCleanupEnabled() { return cleanupEnabled; }
        public void setCleanupEnabled(boolean cleanupEnabled) { this.cleanupEnabled = cleanupEnabled; }

        public String getCleanupCron() { return cleanupCron; }
        public void setCleanupCron(String cleanupCron) { this.cleanupCron = cleanupCron; }

        public int getTemporaryFileHours() { return temporaryFileHours; }
        public void setTemporaryFileHours(int temporaryFileHours) { this.temporaryFileHours = temporaryFileHours; }

        public int getOrphanFileHours() { return orphanFileHours; }
        public void setOrphanFileHours(int orphanFileHours) { this.orphanFileHours = orphanFileHours; }
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

    public static class CalendarFeedProperties {
        private boolean enabled = true;
        private String publicBaseUrl = "http://localhost:8080";
        @Min(0) @Max(365) private int defaultPastDays = 90;
        @Min(1) @Max(36) private int defaultFutureMonths = 18;
        @Min(1) private int maxComponents = 10000;
        @Min(1) private int tombstoneRetentionDays = 90;
        private String suggestedRefresh = "PT6H";
        @Min(1) private int rateLimitPerTokenHour = 120;
        public boolean isEnabled() { return enabled; } public void setEnabled(boolean v) { enabled = v; }
        public String getPublicBaseUrl() { return publicBaseUrl; } public void setPublicBaseUrl(String v) { publicBaseUrl = v; }
        public int getDefaultPastDays() { return defaultPastDays; } public void setDefaultPastDays(int v) { defaultPastDays = v; }
        public int getDefaultFutureMonths() { return defaultFutureMonths; } public void setDefaultFutureMonths(int v) { defaultFutureMonths = v; }
        public int getMaxComponents() { return maxComponents; } public void setMaxComponents(int v) { maxComponents = v; }
        public int getTombstoneRetentionDays() { return tombstoneRetentionDays; } public void setTombstoneRetentionDays(int v) { tombstoneRetentionDays = v; }
        public String getSuggestedRefresh() { return suggestedRefresh; } public void setSuggestedRefresh(String v) { suggestedRefresh = v; }
        public int getRateLimitPerTokenHour() { return rateLimitPerTokenHour; } public void setRateLimitPerTokenHour(int v) { rateLimitPerTokenHour = v; }
    }

    public static class NotificationProperties {
        private long dispatchDelay = 5000;
        private int batchSize = 100;
        private String cleanupCron = "0 30 3 * * *";
        private int outboxRetentionDays = 30;
        private RetryProperties retry = new RetryProperties();

        public long getDispatchDelay() { return dispatchDelay; }
        public void setDispatchDelay(long dispatchDelay) { this.dispatchDelay = dispatchDelay; }
        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
        public String getCleanupCron() { return cleanupCron; }
        public void setCleanupCron(String cleanupCron) { this.cleanupCron = cleanupCron; }
        public int getOutboxRetentionDays() { return outboxRetentionDays; }
        public void setOutboxRetentionDays(int outboxRetentionDays) { this.outboxRetentionDays = outboxRetentionDays; }
        public RetryProperties getRetry() { return retry; }
        public void setRetry(RetryProperties retry) { this.retry = retry; }
    }

    public static class RetryProperties {
        private int initialDelayMinutes = 1;
        private int maxDelayMinutes = 60;
        private int maxAttempts;

        public int getInitialDelayMinutes() { return initialDelayMinutes; }
        public void setInitialDelayMinutes(int initialDelayMinutes) { this.initialDelayMinutes = initialDelayMinutes; }
        public int getMaxDelayMinutes() { return maxDelayMinutes; }
        public void setMaxDelayMinutes(int maxDelayMinutes) { this.maxDelayMinutes = maxDelayMinutes; }
        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    }

    public static class NotificationPreferencesProperties {
        private boolean enabled = true;
        private int defaultCalendarReminderMinutes = 30;
        private String defaultTimeZone = "Europe/Rome";
        private String defaultDigestLocalTime = "08:00";
        private int maxPauseDays = 30;
        private int minSnoozeMinutes = 5;
        private int maxSnoozeDays = 30;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean value) { enabled = value; }
        public int getDefaultCalendarReminderMinutes() { return defaultCalendarReminderMinutes; }
        public void setDefaultCalendarReminderMinutes(int value) { defaultCalendarReminderMinutes = value; }
        public String getDefaultTimeZone() { return defaultTimeZone; }
        public void setDefaultTimeZone(String value) { defaultTimeZone = value; }
        public String getDefaultDigestLocalTime() { return defaultDigestLocalTime; }
        public void setDefaultDigestLocalTime(String value) { defaultDigestLocalTime = value; }
        public int getMaxPauseDays() { return maxPauseDays; }
        public void setMaxPauseDays(int value) { maxPauseDays = value; }
        public int getMinSnoozeMinutes() { return minSnoozeMinutes; }
        public void setMinSnoozeMinutes(int value) { minSnoozeMinutes = value; }
        public int getMaxSnoozeDays() { return maxSnoozeDays; }
        public void setMaxSnoozeDays(int value) { maxSnoozeDays = value; }
    }

    public static class NotificationPushDeliveryProperties {
        private int batchSize = 100;
        private long pollDelay = 5000;
        private int maxAttempts = 8;
        private int retryInitialMinutes = 1;
        private int retryMaxMinutes = 60;
        private int defaultExpirationHours = 24;
        private int deliveredRetentionDays = 30;
        private int skippedRetentionDays = 30;
        private int failedRetentionDays = 90;

        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int value) { batchSize = value; }
        public long getPollDelay() { return pollDelay; }
        public void setPollDelay(long value) { pollDelay = value; }
        public int getMaxAttempts() { return maxAttempts; }
        public void setMaxAttempts(int value) { maxAttempts = value; }
        public int getRetryInitialMinutes() { return retryInitialMinutes; }
        public void setRetryInitialMinutes(int value) { retryInitialMinutes = value; }
        public int getRetryMaxMinutes() { return retryMaxMinutes; }
        public void setRetryMaxMinutes(int value) { retryMaxMinutes = value; }
        public int getDefaultExpirationHours() { return defaultExpirationHours; }
        public void setDefaultExpirationHours(int value) { defaultExpirationHours = value; }
        public int getDeliveredRetentionDays() { return deliveredRetentionDays; }
        public void setDeliveredRetentionDays(int value) { deliveredRetentionDays = value; }
        public int getSkippedRetentionDays() { return skippedRetentionDays; }
        public void setSkippedRetentionDays(int value) { skippedRetentionDays = value; }
        public int getFailedRetentionDays() { return failedRetentionDays; }
        public void setFailedRetentionDays(int value) { failedRetentionDays = value; }
    }

    public static class OnboardingProperties {
        private boolean enabled = true;
        @Min(250) private long workerDelay = 2000;
        private DataSize maxFileSize = DataSize.ofMegabytes(10);
        @Min(1) private int maxTotalRows = 5000;
        @Min(1) private int maxUserRows = 2000;
        @Min(1) @Max(256) private int maxColumns = 64;
        @Min(1) private int maxCellLength = 10000;
        @Min(1) private int maxIssues = 10000;
        @Min(1) private int sourceRetentionDays = 30;
        @Min(1) private int auditRetentionDays = 365;
        @Min(1) private int workerBatchSize = 5;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean value) { enabled = value; }
        public long getWorkerDelay() { return workerDelay; }
        public void setWorkerDelay(long value) { workerDelay = value; }
        public DataSize getMaxFileSize() { return maxFileSize; }
        public void setMaxFileSize(DataSize value) { maxFileSize = value; }
        public int getMaxTotalRows() { return maxTotalRows; }
        public void setMaxTotalRows(int value) { maxTotalRows = value; }
        public int getMaxUserRows() { return maxUserRows; }
        public void setMaxUserRows(int value) { maxUserRows = value; }
        public int getMaxColumns() { return maxColumns; }
        public void setMaxColumns(int value) { maxColumns = value; }
        public int getMaxCellLength() { return maxCellLength; }
        public void setMaxCellLength(int value) { maxCellLength = value; }
        public int getMaxIssues() { return maxIssues; }
        public void setMaxIssues(int value) { maxIssues = value; }
        public int getSourceRetentionDays() { return sourceRetentionDays; }
        public void setSourceRetentionDays(int value) { sourceRetentionDays = value; }
        public int getAuditRetentionDays() { return auditRetentionDays; }
        public void setAuditRetentionDays(int value) { auditRetentionDays = value; }
        public int getWorkerBatchSize() { return workerBatchSize; }
        public void setWorkerBatchSize(int value) { workerBatchSize = value; }

        @AssertTrue(message = "max-user-rows must not exceed max-total-rows")
        public boolean isUserRowsValid() { return maxUserRows <= maxTotalRows; }
    }

    public static class DashboardProperties {
        private boolean enabled;

        @Min(0)
        @Max(366)
        private int calendarLookAheadDays = 14;

        @Min(0)
        @Max(366)
        private int inventoryExpirationLookAheadDays = 30;

        @Min(0)
        @Max(366)
        private int inventoryWarningDays = 7;

        @Min(0)
        @Max(366)
        private int financeUnreconciledWarningDays = 30;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getCalendarLookAheadDays() { return calendarLookAheadDays; }
        public void setCalendarLookAheadDays(int calendarLookAheadDays) { this.calendarLookAheadDays = calendarLookAheadDays; }
        public int getInventoryExpirationLookAheadDays() { return inventoryExpirationLookAheadDays; }
        public void setInventoryExpirationLookAheadDays(int inventoryExpirationLookAheadDays) { this.inventoryExpirationLookAheadDays = inventoryExpirationLookAheadDays; }
        public int getInventoryWarningDays() { return inventoryWarningDays; }
        public void setInventoryWarningDays(int inventoryWarningDays) { this.inventoryWarningDays = inventoryWarningDays; }
        public int getFinanceUnreconciledWarningDays() { return financeUnreconciledWarningDays; }
        public void setFinanceUnreconciledWarningDays(int financeUnreconciledWarningDays) { this.financeUnreconciledWarningDays = financeUnreconciledWarningDays; }

        @AssertTrue(message = "inventory-warning-days must not exceed inventory-expiration-look-ahead-days")
        public boolean isInventoryWarningWindowValid() {
            return inventoryWarningDays <= inventoryExpirationLookAheadDays;
        }
    }
}
