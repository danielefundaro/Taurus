package com.fundaro.zodiac.taurus.service.dto;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MediaDTO extends CommonFieldsOpenSearchDTO {

    @JsonIgnore
    private String path;

    @JsonIgnore
    private String contentType;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof MediaDTO)) {
            return false;
        }

        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getId(), this.getName(), this.getDescription(), this.getPath(), getContentType());
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "QueueUploadFilesDTO{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", path='" + getPath() + "'" +
            ", contentType='" + getContentType() + "'" +
            ", description='" + getDescription() + "'" +
            "}";
    }
}
