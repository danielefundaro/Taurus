package com.fundaro.zodiac.taurus.domain;

import com.fundaro.zodiac.taurus.domain.notification.NotificationPreferencePolicy;
import jakarta.persistence.Column;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;

import java.time.ZonedDateTime;
import jakarta.persistence.Version;

/**
 * A Notices.
 */
@Entity
@Table(name = "notices")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Notices extends CommonFields {

    @NotNull(message = "must not be null")
    @Column(name = "name")
    private String name;

    @Column(name = "message")
    private String message;

    @Column(name = "read_date")
    private ZonedDateTime readDate;

    @Column(name = "source", nullable = false, length = 32)
    private String source = "GENERAL";

    @Column(name = "severity", nullable = false, length = 16)
    private String severity = "INFO";

    @Column(name = "target_path", length = 500)
    private String targetPath;

    @Column(name = "source_event_key", length = 160)
    private String sourceEventKey;

    /** Copia della politica dell'outbox: una riga REQUIRED non offre l'opt-out di categoria. */
    @Enumerated(EnumType.STRING)
    @Column(name = "preference_policy", nullable = false, length = 20)
    private NotificationPreferencePolicy preferencePolicy = NotificationPreferencePolicy.CONFIGURABLE;

    @Column(name = "snoozed_until")
    private ZonedDateTime snoozedUntil;

    @Column(name = "snooze_revision", nullable = false)
    private int snoozeRevision;

    @Version
    @Column(name = "entity_version", nullable = false)
    private long entityVersion;

    public Notices() {
        super();
    }

    public Notices(CommonFields commonFields) {
        super(commonFields);
    }

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Notices id(Long id) {
        this.setId(id);
        return this;
    }

    public Notices deleted(Boolean deleted) {
        this.setDeleted(deleted);
        return this;
    }

    public Notices insertBy(String insertBy) {
        this.setInsertBy(insertBy);
        return this;
    }

    public Notices insertDate(ZonedDateTime insertDate) {
        this.setInsertDate(insertDate);
        return this;
    }

    public Notices editBy(String editBy) {
        this.setEditBy(editBy);
        return this;
    }

    public Notices editDate(ZonedDateTime editDate) {
        this.setEditDate(editDate);
        return this;
    }

    public Notices userId(String userId) {
        this.setUserId(userId);
        return this;
    }

    public String getName() {
        return this.name;
    }

    public Notices name(String name) {
        this.setName(name);
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMessage() {
        return this.message;
    }

    public Notices message(String message) {
        this.setMessage(message);
        return this;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public ZonedDateTime getReadDate() {
        return this.readDate;
    }

    public Notices readDate(ZonedDateTime readDate) {
        this.setReadDate(readDate);
        return this;
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

    public NotificationPreferencePolicy getPreferencePolicy() { return preferencePolicy; }
    public void setPreferencePolicy(NotificationPreferencePolicy value) { preferencePolicy = value; }

    public ZonedDateTime getSnoozedUntil() { return snoozedUntil; }
    public void setSnoozedUntil(ZonedDateTime snoozedUntil) { this.snoozedUntil = snoozedUntil; }
    public int getSnoozeRevision() { return snoozeRevision; }
    public void setSnoozeRevision(int snoozeRevision) { this.snoozeRevision = snoozeRevision; }
    public long getEntityVersion() { return entityVersion; }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Notices)) {
            return false;
        }

        return super.equals(o);
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Notices{" +
            "id=" + getId() +
            ", userId='" + getUserId() + "'" +
            ", name='" + getName() + "'" +
            ", message='" + getMessage() + "'" +
            ", readDate='" + getReadDate() + "'" +
            "}";
    }
}
