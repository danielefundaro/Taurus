package com.fundaro.zodiac.taurus.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;
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

    @Valid
    private EventPreparationProperties eventPreparation = new EventPreparationProperties();

    @Valid
    private ScorePlaybackProperties scorePlayback = new ScorePlaybackProperties();

    @Valid
    private InventoryProperties inventory = new InventoryProperties();

    @Valid
    private SignatureProperties signature = new SignatureProperties();

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

    public CalendarFeedProperties getCalendarFeed() {
        return calendarFeed;
    }

    public void setCalendarFeed(CalendarFeedProperties value) {
        calendarFeed = value;
    }

    public NotificationProperties getNotifications() {
        return notifications;
    }

    public void setNotifications(NotificationProperties notifications) {
        this.notifications = notifications;
    }

    public NotificationPreferencesProperties getNotificationPreferences() {
        return notificationPreferences;
    }

    public void setNotificationPreferences(NotificationPreferencesProperties value) {
        notificationPreferences = value;
    }

    public NotificationPushDeliveryProperties getNotificationPushDelivery() {
        return notificationPushDelivery;
    }

    public void setNotificationPushDelivery(NotificationPushDeliveryProperties value) {
        notificationPushDelivery = value;
    }

    public DashboardProperties getDashboard() {
        return dashboard;
    }

    public void setDashboard(DashboardProperties dashboard) {
        this.dashboard = dashboard;
    }

    public OnboardingProperties getOnboarding() {
        return onboarding;
    }

    public void setOnboarding(OnboardingProperties onboarding) {
        this.onboarding = onboarding;
    }

    public EventPreparationProperties getEventPreparation() {
        return eventPreparation;
    }

    public void setEventPreparation(EventPreparationProperties value) {
        eventPreparation = value;
    }

    public ScorePlaybackProperties getScorePlayback() {
        return scorePlayback;
    }

    public void setScorePlayback(ScorePlaybackProperties value) {
        scorePlayback = value;
    }

    public InventoryProperties getInventory() {
        return inventory;
    }

    public void setInventory(InventoryProperties value) {
        inventory = value;
    }

    public SignatureProperties getSignature() {
        return signature;
    }

    public void setSignature(SignatureProperties value) {
        signature = value;
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

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getDataPath() {
            return dataPath;
        }

        public void setDataPath(String dataPath) {
            this.dataPath = dataPath;
        }

        public String getLanguage() {
            return language;
        }

        public void setLanguage(String language) {
            this.language = language;
        }
    }

    public static class VapidProperties {
        private String publicKey = "CHANGE_ME";
        private String privateKey = "CHANGE_ME";
        private String subject = "mailto:admin@taurus.it";

        public String getPublicKey() {
            return publicKey;
        }

        public void setPublicKey(String publicKey) {
            this.publicKey = publicKey;
        }

        public String getPrivateKey() {
            return privateKey;
        }

        public void setPrivateKey(String privateKey) {
            this.privateKey = privateKey;
        }

        public String getSubject() {
            return subject;
        }

        public void setSubject(String subject) {
            this.subject = subject;
        }
    }

    /**
     * Pulizia periodica dei residui nello storage dei tenant.
     */
    public static class MediaProperties {
        private boolean cleanupEnabled = true;
        private String cleanupCron = "0 30 3 * * *";
        private int temporaryFileHours = 24;
        private int orphanFileHours = 168;
        private int trackPageEditReceiptDays = 7;

        public boolean isCleanupEnabled() {
            return cleanupEnabled;
        }

        public void setCleanupEnabled(boolean cleanupEnabled) {
            this.cleanupEnabled = cleanupEnabled;
        }

        public String getCleanupCron() {
            return cleanupCron;
        }

        public void setCleanupCron(String cleanupCron) {
            this.cleanupCron = cleanupCron;
        }

        public int getTemporaryFileHours() {
            return temporaryFileHours;
        }

        public void setTemporaryFileHours(int temporaryFileHours) {
            this.temporaryFileHours = temporaryFileHours;
        }

        public int getOrphanFileHours() {
            return orphanFileHours;
        }

        public void setOrphanFileHours(int orphanFileHours) {
            this.orphanFileHours = orphanFileHours;
        }

        public int getTrackPageEditReceiptDays() {
            return trackPageEditReceiptDays;
        }

        public void setTrackPageEditReceiptDays(int trackPageEditReceiptDays) {
            this.trackPageEditReceiptDays = trackPageEditReceiptDays;
        }
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

        public boolean isCleanupEnabled() {
            return cleanupEnabled;
        }

        public void setCleanupEnabled(boolean cleanupEnabled) {
            this.cleanupEnabled = cleanupEnabled;
        }

        public String getCleanupCron() {
            return cleanupCron;
        }

        public void setCleanupCron(String cleanupCron) {
            this.cleanupCron = cleanupCron;
        }

        public int getNoticesDays() {
            return noticesDays;
        }

        public void setNoticesDays(int noticesDays) {
            this.noticesDays = noticesDays;
        }

        public int getLastResearchDays() {
            return lastResearchDays;
        }

        public void setLastResearchDays(int lastResearchDays) {
            this.lastResearchDays = lastResearchDays;
        }

        public int getSentPushRemindersDays() {
            return sentPushRemindersDays;
        }

        public void setSentPushRemindersDays(int sentPushRemindersDays) {
            this.sentPushRemindersDays = sentPushRemindersDays;
        }

        public List<String> getTenantIndices() {
            return tenantIndices;
        }

        public void setTenantIndices(List<String> tenantIndices) {
            this.tenantIndices = tenantIndices;
        }
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
        @Min(0)
        @Max(365)
        private int defaultPastDays = 90;
        @Min(1)
        @Max(36)
        private int defaultFutureMonths = 18;
        @Min(1)
        private int maxComponents = 10000;
        @Min(1)
        private int tombstoneRetentionDays = 90;
        private String suggestedRefresh = "PT6H";
        @Min(1)
        private int rateLimitPerTokenHour = 120;
        @Min(1)
        private int rateLimitPerIpHour = 2000;
        @Min(1)
        private int rateLimitGlobalHour = 100000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean v) {
            enabled = v;
        }

        public String getPublicBaseUrl() {
            return publicBaseUrl;
        }

        public void setPublicBaseUrl(String v) {
            publicBaseUrl = v;
        }

        public int getDefaultPastDays() {
            return defaultPastDays;
        }

        public void setDefaultPastDays(int v) {
            defaultPastDays = v;
        }

        public int getDefaultFutureMonths() {
            return defaultFutureMonths;
        }

        public void setDefaultFutureMonths(int v) {
            defaultFutureMonths = v;
        }

        public int getMaxComponents() {
            return maxComponents;
        }

        public void setMaxComponents(int v) {
            maxComponents = v;
        }

        public int getTombstoneRetentionDays() {
            return tombstoneRetentionDays;
        }

        public void setTombstoneRetentionDays(int v) {
            tombstoneRetentionDays = v;
        }

        public String getSuggestedRefresh() {
            return suggestedRefresh;
        }

        public void setSuggestedRefresh(String v) {
            suggestedRefresh = v;
        }

        public int getRateLimitPerTokenHour() {
            return rateLimitPerTokenHour;
        }

        public void setRateLimitPerTokenHour(int v) {
            rateLimitPerTokenHour = v;
        }

        public int getRateLimitPerIpHour() {
            return rateLimitPerIpHour;
        }

        public void setRateLimitPerIpHour(int v) {
            rateLimitPerIpHour = v;
        }

        public int getRateLimitGlobalHour() {
            return rateLimitGlobalHour;
        }

        public void setRateLimitGlobalHour(int v) {
            rateLimitGlobalHour = v;
        }
    }

    public static class NotificationProperties {
        private long dispatchDelay = 5000;
        private int batchSize = 100;
        private String cleanupCron = "0 30 3 * * *";
        private int outboxRetentionDays = 30;
        private RetryProperties retry = new RetryProperties();

        public long getDispatchDelay() {
            return dispatchDelay;
        }

        public void setDispatchDelay(long dispatchDelay) {
            this.dispatchDelay = dispatchDelay;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public String getCleanupCron() {
            return cleanupCron;
        }

        public void setCleanupCron(String cleanupCron) {
            this.cleanupCron = cleanupCron;
        }

        public int getOutboxRetentionDays() {
            return outboxRetentionDays;
        }

        public void setOutboxRetentionDays(int outboxRetentionDays) {
            this.outboxRetentionDays = outboxRetentionDays;
        }

        public RetryProperties getRetry() {
            return retry;
        }

        public void setRetry(RetryProperties retry) {
            this.retry = retry;
        }
    }

    public static class RetryProperties {
        private int initialDelayMinutes = 1;
        private int maxDelayMinutes = 60;
        private int maxAttempts;

        public int getInitialDelayMinutes() {
            return initialDelayMinutes;
        }

        public void setInitialDelayMinutes(int initialDelayMinutes) {
            this.initialDelayMinutes = initialDelayMinutes;
        }

        public int getMaxDelayMinutes() {
            return maxDelayMinutes;
        }

        public void setMaxDelayMinutes(int maxDelayMinutes) {
            this.maxDelayMinutes = maxDelayMinutes;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }
    }

    public static class NotificationPreferencesProperties {
        private boolean enabled = true;
        private int defaultCalendarReminderMinutes = 30;
        private String defaultTimeZone = "Europe/Rome";
        private String defaultDigestLocalTime = "08:00";
        private int maxPauseDays = 30;
        private int minSnoozeMinutes = 5;
        private int maxSnoozeDays = 30;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean value) {
            enabled = value;
        }

        public int getDefaultCalendarReminderMinutes() {
            return defaultCalendarReminderMinutes;
        }

        public void setDefaultCalendarReminderMinutes(int value) {
            defaultCalendarReminderMinutes = value;
        }

        public String getDefaultTimeZone() {
            return defaultTimeZone;
        }

        public void setDefaultTimeZone(String value) {
            defaultTimeZone = value;
        }

        public String getDefaultDigestLocalTime() {
            return defaultDigestLocalTime;
        }

        public void setDefaultDigestLocalTime(String value) {
            defaultDigestLocalTime = value;
        }

        public int getMaxPauseDays() {
            return maxPauseDays;
        }

        public void setMaxPauseDays(int value) {
            maxPauseDays = value;
        }

        public int getMinSnoozeMinutes() {
            return minSnoozeMinutes;
        }

        public void setMinSnoozeMinutes(int value) {
            minSnoozeMinutes = value;
        }

        public int getMaxSnoozeDays() {
            return maxSnoozeDays;
        }

        public void setMaxSnoozeDays(int value) {
            maxSnoozeDays = value;
        }
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

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int value) {
            batchSize = value;
        }

        public long getPollDelay() {
            return pollDelay;
        }

        public void setPollDelay(long value) {
            pollDelay = value;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int value) {
            maxAttempts = value;
        }

        public int getRetryInitialMinutes() {
            return retryInitialMinutes;
        }

        public void setRetryInitialMinutes(int value) {
            retryInitialMinutes = value;
        }

        public int getRetryMaxMinutes() {
            return retryMaxMinutes;
        }

        public void setRetryMaxMinutes(int value) {
            retryMaxMinutes = value;
        }

        public int getDefaultExpirationHours() {
            return defaultExpirationHours;
        }

        public void setDefaultExpirationHours(int value) {
            defaultExpirationHours = value;
        }

        public int getDeliveredRetentionDays() {
            return deliveredRetentionDays;
        }

        public void setDeliveredRetentionDays(int value) {
            deliveredRetentionDays = value;
        }

        public int getSkippedRetentionDays() {
            return skippedRetentionDays;
        }

        public void setSkippedRetentionDays(int value) {
            skippedRetentionDays = value;
        }

        public int getFailedRetentionDays() {
            return failedRetentionDays;
        }

        public void setFailedRetentionDays(int value) {
            failedRetentionDays = value;
        }
    }

    public static class OnboardingProperties {
        private boolean enabled = true;
        private boolean recoveryEnabled = true;
        @Min(250)
        private long workerDelay = 2000;
        private DataSize maxFileSize = DataSize.ofMegabytes(10);
        @Min(1)
        private int maxTotalRows = 5000;
        @Min(1)
        private int maxUserRows = 2000;
        @Min(1)
        @Max(256)
        private int maxColumns = 64;
        @Min(1)
        private int maxCellLength = 10000;
        @Min(1)
        private int maxIssues = 10000;
        @Min(1)
        private int sourceRetentionDays = 30;
        @Min(1)
        private int auditRetentionDays = 365;
        @Min(1)
        private int workerBatchSize = 5;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean value) {
            enabled = value;
        }

        public boolean isRecoveryEnabled() {
            return recoveryEnabled;
        }

        public void setRecoveryEnabled(boolean value) {
            recoveryEnabled = value;
        }

        public long getWorkerDelay() {
            return workerDelay;
        }

        public void setWorkerDelay(long value) {
            workerDelay = value;
        }

        public DataSize getMaxFileSize() {
            return maxFileSize;
        }

        public void setMaxFileSize(DataSize value) {
            maxFileSize = value;
        }

        public int getMaxTotalRows() {
            return maxTotalRows;
        }

        public void setMaxTotalRows(int value) {
            maxTotalRows = value;
        }

        public int getMaxUserRows() {
            return maxUserRows;
        }

        public void setMaxUserRows(int value) {
            maxUserRows = value;
        }

        public int getMaxColumns() {
            return maxColumns;
        }

        public void setMaxColumns(int value) {
            maxColumns = value;
        }

        public int getMaxCellLength() {
            return maxCellLength;
        }

        public void setMaxCellLength(int value) {
            maxCellLength = value;
        }

        public int getMaxIssues() {
            return maxIssues;
        }

        public void setMaxIssues(int value) {
            maxIssues = value;
        }

        public int getSourceRetentionDays() {
            return sourceRetentionDays;
        }

        public void setSourceRetentionDays(int value) {
            sourceRetentionDays = value;
        }

        public int getAuditRetentionDays() {
            return auditRetentionDays;
        }

        public void setAuditRetentionDays(int value) {
            auditRetentionDays = value;
        }

        public int getWorkerBatchSize() {
            return workerBatchSize;
        }

        public void setWorkerBatchSize(int value) {
            workerBatchSize = value;
        }

        @AssertTrue(message = "max-user-rows must not exceed max-total-rows")
        public boolean isUserRowsValid() {
            return maxUserRows <= maxTotalRows;
        }
    }

    public static class EventPreparationProperties {
        private boolean enabled;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean value) {
            enabled = value;
        }
    }

    public static class ScorePlaybackProperties {
        private boolean enabled;

        /** Largest MusicXML or MIDI file accepted on upload, compressed or not. */
        private long maxUploadBytes = 10L * 1024 * 1024;

        /** Versions kept for each part by the nightly cleanup, besides the current and the approved ones. */
        @Min(1)
        private int keptVersions = 20;

        private String cleanupCron = "0 15 4 * * *";

        @Valid
        private OmrProperties omr = new OmrProperties();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean value) {
            enabled = value;
        }

        public long getMaxUploadBytes() {
            return maxUploadBytes;
        }

        public void setMaxUploadBytes(long value) {
            maxUploadBytes = value;
        }

        public int getKeptVersions() {
            return keptVersions;
        }

        public void setKeptVersions(int value) {
            keptVersions = value;
        }

        public String getCleanupCron() {
            return cleanupCron;
        }

        public void setCleanupCron(String value) {
            cleanupCron = value;
        }

        public OmrProperties getOmr() {
            return omr;
        }

        public void setOmr(OmrProperties value) {
            omr = value;
        }
    }

    /** The optical music recognition service. Without a URL recognition and MIDI upload are not offered. */
    public static class OmrProperties {
        private String url = "";

        /** Key shared with the service, sent in the X-Taurus-Omr-Key header. */
        private String apiKey = "";

        /** Longest wait for a recognition: both engines, all the pages. */
        private Duration timeout = Duration.ofMinutes(12);

        private Duration connectTimeout = Duration.ofSeconds(5);

        /** Recognition jobs run at the same time by this backend instance. */
        @Min(1)
        @Max(8)
        private int concurrency = 1;

        /** A running job not finished after this time is queued again, at most {@code maxAttempts} times in total. */
        private Duration staleAfter = Duration.ofMinutes(30);

        @Min(1)
        private int maxAttempts = 3;

        /** Requeues interrupted jobs and republishes lost messages; disabled in tests that drive jobs by hand. */
        private boolean recoveryEnabled = true;

        public boolean isConfigured() {
            return url != null && !url.isBlank();
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String value) {
            url = value;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String value) {
            apiKey = value;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration value) {
            timeout = value;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(Duration value) {
            connectTimeout = value;
        }

        public int getConcurrency() {
            return concurrency;
        }

        public void setConcurrency(int value) {
            concurrency = value;
        }

        public Duration getStaleAfter() {
            return staleAfter;
        }

        public void setStaleAfter(Duration value) {
            staleAfter = value;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int value) {
            maxAttempts = value;
        }

        public boolean isRecoveryEnabled() {
            return recoveryEnabled;
        }

        public void setRecoveryEnabled(boolean value) {
            recoveryEnabled = value;
        }
    }

    public static class InventoryProperties {
        @Valid
        private QrProperties qr = new QrProperties();

        public QrProperties getQr() {
            return qr;
        }

        public void setQr(QrProperties value) {
            qr = value;
        }
    }

    public static class QrProperties {
        private boolean enabled;
        private String publicBaseUrl;
        @Min(1)
        @Max(1000)
        private int resolutionLimitPerMinute = 60;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean value) {
            enabled = value;
        }

        public String getPublicBaseUrl() {
            return publicBaseUrl;
        }

        public void setPublicBaseUrl(String value) {
            publicBaseUrl = value;
        }

        public int getResolutionLimitPerMinute() {
            return resolutionLimitPerMinute;
        }

        public void setResolutionLimitPerMinute(int value) {
            resolutionLimitPerMinute = value;
        }

        @AssertTrue(message = "inventory.qr.public-base-url must be an HTTPS URL without credentials, query or fragment; HTTP is allowed only for localhost")
        public boolean isPublicBaseUrlValid() {
            if (!enabled) return true;
            try {
                URI uri = URI.create(publicBaseUrl == null ? "" : publicBaseUrl.trim());
                if (!uri.isAbsolute() || uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null)
                    return false;
                if ("https".equalsIgnoreCase(uri.getScheme())) return true;
                return "http".equalsIgnoreCase(uri.getScheme())
                    && ("localhost".equalsIgnoreCase(uri.getHost()) || "127.0.0.1".equals(uri.getHost()) || "::1".equals(uri.getHost()));
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }
    }

    /**
     * Electronic signature of loan sheets and assignment acknowledgments: confirmation codes and seal of the documents.
     */
    public static class SignatureProperties {
        /**
         * Key of the HMAC that protects the stored confirmation codes; electronic signature cannot be enabled without it.
         */
        private String otpSecret;

        private Duration codeValidity = Duration.ofMinutes(10);

        @Min(1)
        @Max(10)
        private int maxAttempts = 5;

        private Duration resendInterval = Duration.ofSeconds(60);

        @Min(1)
        @Max(10)
        private int maxSends = 5;

        @Min(1)
        @Max(50)
        private int maxBatchItems = 50;

        /**
         * Codes a user can request in an hour, to protect the mailbox of the association.
         */
        @Min(1)
        @Max(1000)
        private int maxChallengesPerHour = 20;

        @Min(1)
        @Max(366)
        private int challengeRetentionDays = 30;

        private String cleanupCron = "0 45 3 * * *";

        /**
         * Days the signed acts of a settled or cancelled loan sheet stay downloadable before Taurus removes them: the
         * tenant keeps them for the legal retention period.
         */
        @Min(1)
        @Max(366)
        private int downloadGraceDays = 30;

        private String purgeCron = "0 50 3 * * *";

        /**
         * Address of the application, from which the links sent to the parents are built; never taken from the
         * {@code Host} header of a request.
         */
        private String publicBaseUrl = "http://localhost:8080";

        /**
         * Validity of a link sent to a parent or guardian without an account.
         */
        private Duration linkValidity = Duration.ofDays(7);

        @Min(1)
        @Max(10000)
        private int linkRateLimitPerTokenHour = 120;

        @Min(1)
        @Max(100000)
        private int linkRateLimitPerIpHour = 600;

        /**
         * When the links expired without a signature are notified to the administrators.
         */
        private String linkExpiryCron = "0 20 * * * *";

        @Valid
        private SealProperties seal = new SealProperties();

        @Valid
        private SignatureKeycloakProperties keycloak = new SignatureKeycloakProperties();

        @Valid
        private CieProperties cie = new CieProperties();

        public String getOtpSecret() {
            return otpSecret;
        }

        public void setOtpSecret(String value) {
            otpSecret = value;
        }

        public Duration getCodeValidity() {
            return codeValidity;
        }

        public void setCodeValidity(Duration value) {
            codeValidity = value;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int value) {
            maxAttempts = value;
        }

        public Duration getResendInterval() {
            return resendInterval;
        }

        public void setResendInterval(Duration value) {
            resendInterval = value;
        }

        public int getMaxSends() {
            return maxSends;
        }

        public void setMaxSends(int value) {
            maxSends = value;
        }

        public int getMaxBatchItems() {
            return maxBatchItems;
        }

        public void setMaxBatchItems(int value) {
            maxBatchItems = value;
        }

        public int getMaxChallengesPerHour() {
            return maxChallengesPerHour;
        }

        public void setMaxChallengesPerHour(int value) {
            maxChallengesPerHour = value;
        }

        public int getChallengeRetentionDays() {
            return challengeRetentionDays;
        }

        public void setChallengeRetentionDays(int value) {
            challengeRetentionDays = value;
        }

        public String getCleanupCron() {
            return cleanupCron;
        }

        public void setCleanupCron(String value) {
            cleanupCron = value;
        }

        public int getDownloadGraceDays() {
            return downloadGraceDays;
        }

        public void setDownloadGraceDays(int value) {
            downloadGraceDays = value;
        }

        public String getPurgeCron() {
            return purgeCron;
        }

        public void setPurgeCron(String value) {
            purgeCron = value;
        }

        public String getPublicBaseUrl() {
            return publicBaseUrl;
        }

        public void setPublicBaseUrl(String value) {
            publicBaseUrl = value;
        }

        public Duration getLinkValidity() {
            return linkValidity;
        }

        public void setLinkValidity(Duration value) {
            linkValidity = value;
        }

        public int getLinkRateLimitPerTokenHour() {
            return linkRateLimitPerTokenHour;
        }

        public void setLinkRateLimitPerTokenHour(int value) {
            linkRateLimitPerTokenHour = value;
        }

        public int getLinkRateLimitPerIpHour() {
            return linkRateLimitPerIpHour;
        }

        public void setLinkRateLimitPerIpHour(int value) {
            linkRateLimitPerIpHour = value;
        }

        public String getLinkExpiryCron() {
            return linkExpiryCron;
        }

        public void setLinkExpiryCron(String value) {
            linkExpiryCron = value;
        }

        public SealProperties getSeal() {
            return seal;
        }

        public void setSeal(SealProperties value) {
            seal = value;
        }

        public SignatureKeycloakProperties getKeycloak() {
            return keycloak;
        }

        public void setKeycloak(SignatureKeycloakProperties value) {
            keycloak = value;
        }

        public CieProperties getCie() {
            return cie;
        }

        public void setCie(CieProperties value) {
            cie = value;
        }

        @AssertTrue(message = "signature.code-validity, signature.resend-interval and signature.link-validity must be positive")
        public boolean isDurationsValid() {
            return codeValidity != null && !codeValidity.isNegative() && !codeValidity.isZero()
                && resendInterval != null && !resendInterval.isNegative()
                && linkValidity != null && !linkValidity.isNegative() && !linkValidity.isZero();
        }
    }

    /**
     * Verification of the documents signed with CieSign: CieSign is offered only when the certificates of the CIE
     * certification authorities are configured.
     */
    public static class CieProperties {
        /**
         * PEM or DER files with the root and intermediate certificates of the CIE, trusted as anchors; a location can
         * start with {@code file:} or {@code classpath:}.
         */
        private List<String> trustAnchors = List.of();

        /**
         * Time allowed to download the revocation list named in the certificate; past it the signature is accepted
         * and the evidence records that revocation could not be checked.
         */
        private Duration revocationTimeout = Duration.ofSeconds(10);

        public List<String> getTrustAnchors() {
            return trustAnchors;
        }

        public void setTrustAnchors(List<String> value) {
            trustAnchors = value == null ? List.of() : value;
        }

        public Duration getRevocationTimeout() {
            return revocationTimeout;
        }

        public void setRevocationTimeout(Duration value) {
            revocationTimeout = value;
        }

        @AssertTrue(message = "signature.cie.revocation-timeout must be positive")
        public boolean isRevocationTimeoutValid() {
            return revocationTimeout != null && !revocationTimeout.isNegative() && !revocationTimeout.isZero();
        }
    }

    /**
     * Confidential client that verifies the codes of the authentication app with the {@code taurus-otp} resource of
     * Keycloak. The authentication app is offered only when the client secret is set.
     */
    public static class SignatureKeycloakProperties {
        /**
         * Realm URL, for example {@code http://localhost:9080/realms/taurus}.
         */
        private String realmUri;

        private String clientId = "taurus-signature";

        private String clientSecret;

        private Duration timeout = Duration.ofSeconds(5);

        public boolean isConfigured() {
            return realmUri != null && !realmUri.isBlank() && clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
        }

        public String getRealmUri() {
            return realmUri;
        }

        public void setRealmUri(String value) {
            realmUri = value;
        }

        public String getClientId() {
            return clientId;
        }

        public void setClientId(String value) {
            clientId = value;
        }

        public String getClientSecret() {
            return clientSecret;
        }

        public void setClientSecret(String value) {
            clientSecret = value;
        }

        public Duration getTimeout() {
            return timeout;
        }

        public void setTimeout(Duration value) {
            timeout = value;
        }

        @AssertTrue(message = "signature.keycloak.timeout must be positive")
        public boolean isTimeoutValid() {
            return timeout != null && !timeout.isNegative() && !timeout.isZero();
        }
    }

    /**
     * Certificate of the installation that seals the signed documents and the attestations.
     */
    public static class SealProperties {
        /**
         * PKCS#12 keystore with the private key and the certificate chain of the seal.
         */
        private String keystorePath;

        private String keystorePassword;

        /**
         * Alias of the key in the keystore; the first key entry when empty.
         */
        private String keyAlias;

        /**
         * Generates a temporary self-signed certificate at startup when no keystore is configured. Development only.
         */
        private boolean ephemeral;

        /**
         * RFC 3161 time-stamping service: signatures are PAdES B-T when set, B-B otherwise.
         */
        private String tsaUrl;

        public String getKeystorePath() {
            return keystorePath;
        }

        public void setKeystorePath(String value) {
            keystorePath = value;
        }

        public String getKeystorePassword() {
            return keystorePassword;
        }

        public void setKeystorePassword(String value) {
            keystorePassword = value;
        }

        public String getKeyAlias() {
            return keyAlias;
        }

        public void setKeyAlias(String value) {
            keyAlias = value;
        }

        public boolean isEphemeral() {
            return ephemeral;
        }

        public void setEphemeral(boolean value) {
            ephemeral = value;
        }

        public String getTsaUrl() {
            return tsaUrl;
        }

        public void setTsaUrl(String value) {
            tsaUrl = value;
        }
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

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int getCalendarLookAheadDays() {
            return calendarLookAheadDays;
        }

        public void setCalendarLookAheadDays(int calendarLookAheadDays) {
            this.calendarLookAheadDays = calendarLookAheadDays;
        }

        public int getInventoryExpirationLookAheadDays() {
            return inventoryExpirationLookAheadDays;
        }

        public void setInventoryExpirationLookAheadDays(int inventoryExpirationLookAheadDays) {
            this.inventoryExpirationLookAheadDays = inventoryExpirationLookAheadDays;
        }

        public int getInventoryWarningDays() {
            return inventoryWarningDays;
        }

        public void setInventoryWarningDays(int inventoryWarningDays) {
            this.inventoryWarningDays = inventoryWarningDays;
        }

        public int getFinanceUnreconciledWarningDays() {
            return financeUnreconciledWarningDays;
        }

        public void setFinanceUnreconciledWarningDays(int financeUnreconciledWarningDays) {
            this.financeUnreconciledWarningDays = financeUnreconciledWarningDays;
        }

        @AssertTrue(message = "inventory-warning-days must not exceed inventory-expiration-look-ahead-days")
        public boolean isInventoryWarningWindowValid() {
            return inventoryWarningDays <= inventoryExpirationLookAheadDays;
        }
    }
}
