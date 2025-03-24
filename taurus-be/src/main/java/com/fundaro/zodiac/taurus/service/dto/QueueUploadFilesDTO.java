package com.fundaro.zodiac.taurus.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fundaro.zodiac.taurus.domain.enumeration.UploadFileStatus;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.codec.multipart.FilePart;

import java.util.Objects;

/**
 * A DTO for the {@link com.fundaro.zodiac.taurus.domain.QueueUploadFiles} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class QueueUploadFilesDTO extends CommonFieldsOpenSearchDTO {

    @NotNull
    private String userId;

    @JsonIgnore
    private String path;

    private String trackId;

    @NotNull
    private UploadFileStatus status;

    private String type;

    @JsonIgnore
    private FilePart filePart;

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getTrackId() {
        return trackId;
    }

    public void setTrackId(String trackId) {
        this.trackId = trackId;
    }

    public UploadFileStatus getStatus() {
        return status;
    }

    public void setStatus(UploadFileStatus status) {
        this.status = status;
    }

    public FilePart getFilePart() {
        return filePart;
    }

    public void setFilePart(FilePart filePart) {
        this.filePart = filePart;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof QueueUploadFilesDTO)) {
            return false;
        }

        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getId(), this.getName(), this.getDescription(), this.getUserId(), this.getTrackId(), this.getStatus(), this.getType(), this.getPath());
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "QueueUploadFilesDTO{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", userId='" + getUserId() + "'" +
            ", trackId='" + getTrackId() + "'" +
            ", status='" + getStatus() + "'" +
            ", type='" + getType() + "'" +
            ", description='" + getDescription() + "'" +
            "}";
    }
}
