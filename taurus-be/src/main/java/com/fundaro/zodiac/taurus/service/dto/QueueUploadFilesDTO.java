package com.fundaro.zodiac.taurus.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fundaro.zodiac.taurus.domain.enumeration.UploadFileStatusEnum;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.multipart.MultipartFile;

import java.util.Objects;

/**
 * A DTO for the {@link com.fundaro.zodiac.taurus.domain.QueueUploadFiles} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class QueueUploadFilesDTO extends CommonFieldsOpenSearchDTO {

    private Long userId;

    private Long sourceMediaAssetId;

    private Long trackId;

    @NotNull
    private UploadFileStatusEnum status;

    private String type;

    @JsonIgnore
    private MultipartFile multipartFile;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getSourceMediaAssetId() {
        return sourceMediaAssetId;
    }

    public void setSourceMediaAssetId(Long sourceMediaAssetId) {
        this.sourceMediaAssetId = sourceMediaAssetId;
    }

    public Long getTrackId() {
        return trackId;
    }

    public void setTrackId(Long trackId) {
        this.trackId = trackId;
    }

    public UploadFileStatusEnum getStatus() {
        return status;
    }

    public void setStatus(UploadFileStatusEnum status) {
        this.status = status;
    }

    public MultipartFile getMultipartFile() {
        return multipartFile;
    }

    public void setMultipartFile(MultipartFile multipartFile) {
        this.multipartFile = multipartFile;
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
        return Objects.hash(this.getId(), this.getName(), this.getDescription(), this.getUserId(), this.getTrackId(), this.getStatus(), this.getType(), this.getSourceMediaAssetId());
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
