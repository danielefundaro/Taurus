package com.fundaro.zodiac.taurus.domain;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Media extends CommonFieldsOpenSearch {

    private String path;

    @JsonProperty("content_type")
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
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof Media)) {
            return false;
        }

        return getPath() != null && getPath().equals(((Media) o).getPath());
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Media{" +
            (getPath() != null ? "index=" + getPath() : "") +
            (getContentType() != null ? "name=" + getContentType() : "") +
            "}";
    }
}
