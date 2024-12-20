package com.fundaro.zodiac.taurus.service.dto;

import com.fundaro.zodiac.taurus.domain.enumeration.PieceTypeEnum;
import jakarta.validation.constraints.NotNull;

/**
 * A DTO for the {@link com.fundaro.zodiac.taurus.domain.Pieces} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class PiecesDTO extends CommonFieldsDTO {

    @NotNull(message = "must not be null")
    private String name;

    private String description;

    @NotNull(message = "must not be null")
    private PieceTypeEnum type;

    @NotNull(message = "must not be null")
    private String contentType;

    @NotNull(message = "must not be null")
    private String path;

    private Long orderNumber;

    @NotNull
    private MediaDTO media;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public PieceTypeEnum getType() {
        return type;
    }

    public void setType(PieceTypeEnum type) {
        this.type = type;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Long getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(Long orderNumber) {
        this.orderNumber = orderNumber;
    }

    public MediaDTO getMedia() {
        return media;
    }

    public void setMedia(MediaDTO media) {
        this.media = media;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PiecesDTO)) {
            return false;
        }

        return super.equals(o);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "PiecesDTO{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", description='" + getDescription() + "'" +
            ", type='" + getType() + "'" +
            ", contentType='" + getContentType() + "'" +
            ", path='" + getPath() + "'" +
            ", orderNumber=" + getOrderNumber() +
            ", media=" + getMedia() +
            "}";
    }
}
