package com.fundaro.zodiac.taurus.service.dto;

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
