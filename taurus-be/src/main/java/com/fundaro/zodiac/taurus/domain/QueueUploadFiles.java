package com.fundaro.zodiac.taurus.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fundaro.zodiac.taurus.domain.enumeration.UploadFileStatus;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import org.springframework.data.relational.core.mapping.Table;

/**
 * A QueueUploadFiles.
 */
@Table("queue_upload_files")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class QueueUploadFiles extends CommonFieldsOpenSearch {

    @JsonProperty("user_id")
    private String userId;

    private String path;

    @JsonProperty("track_id")
    private String trackId;

    @Enumerated(EnumType.STRING)
    private UploadFileStatus status;

    private String type;

    public String getUserId() {
        return this.userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getPath() {
        return this.path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getTrackId() {
        return this.trackId;
    }

    public void setTrackId(String trackId) {
        this.trackId = trackId;
    }

    public UploadFileStatus getStatus() {
        return this.status;
    }

    public void setStatus(UploadFileStatus status) {
        this.status = status;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
// jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof QueueUploadFiles)) {
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
        return "QueueUploadFiles{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", userId='" + getUserId() + "'" +
            ", path='" + getPath() + "'" +
            ", trackId='" + getTrackId() + "'" +
            ", status='" + getStatus() + "'" +
            ", type='" + getType() + "'" +
            ", description='" + getDescription() + "'" +
            "}";
    }
}
