package com.fundaro.zodiac.taurus.service.dto;

import com.fundaro.zodiac.taurus.domain.notification.NotificationPreferencePolicy;
import jakarta.validation.constraints.NotNull;

import java.time.ZonedDateTime;

/**
 * A DTO for the {@link com.fundaro.zodiac.taurus.domain.Notices} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class NoticesDTO extends CommonFieldsDTO {

    @NotNull(message = "must not be null")
    private String name;

    private String message;

    private ZonedDateTime readDate;

    @NotNull(message = "must not be null")
    private String source = "GENERAL";

    @NotNull(message = "must not be null")
    private String severity = "INFO";

    private String targetPath;

    private String sourceEventKey;

    private NotificationPreferencePolicy preferencePolicy = NotificationPreferencePolicy.CONFIGURABLE;

    private ZonedDateTime snoozedUntil;

    private int snoozeRevision;

    private long entityVersion;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public ZonedDateTime getReadDate() {
        return readDate;
    }

    public void setReadDate(ZonedDateTime readDate) {
        this.readDate = readDate;
    }

    public String getSource() { return source; }

    public void setSource(String source) { this.source = source; }

    public String getSeverity() { return severity; }

    public void setSeverity(String severity) { this.severity = severity; }

    public String getTargetPath() { return targetPath; }

    public void setTargetPath(String targetPath) { this.targetPath = targetPath; }

    public String getSourceEventKey() { return sourceEventKey; }

    public void setSourceEventKey(String sourceEventKey) { this.sourceEventKey = sourceEventKey; }

    public NotificationPreferencePolicy getPreferencePolicy() {
        return preferencePolicy;
    }

    public void setPreferencePolicy(NotificationPreferencePolicy preferencePolicy) {
        this.preferencePolicy = preferencePolicy;
    }

    public ZonedDateTime getSnoozedUntil() { return snoozedUntil; }
    public void setSnoozedUntil(ZonedDateTime snoozedUntil) { this.snoozedUntil = snoozedUntil; }
    public int getSnoozeRevision() { return snoozeRevision; }
    public void setSnoozeRevision(int snoozeRevision) { this.snoozeRevision = snoozeRevision; }
    public long getEntityVersion() { return entityVersion; }
    public void setEntityVersion(long entityVersion) { this.entityVersion = entityVersion; }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NoticesDTO)) {
            return false;
        }

        return super.equals(o);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "NoticesDTO{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", message='" + getMessage() + "'" +
            ", readDate='" + getReadDate() + "'" +
            "}";
    }
}
