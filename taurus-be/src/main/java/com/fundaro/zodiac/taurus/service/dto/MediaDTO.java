package com.fundaro.zodiac.taurus.service.dto;

import jakarta.validation.constraints.NotNull;

/**
 * A DTO for the {@link com.fundaro.zodiac.taurus.domain.Media} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class MediaDTO extends CommonFieldsDTO {

    private String name;

    private String description;

    private Long orderNumber;

    @NotNull
    private TracksDTO track;

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

    public Long getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(Long orderNumber) {
        this.orderNumber = orderNumber;
    }

    public TracksDTO getTrack() {
        return track;
    }

    public void setTrack(TracksDTO track) {
        this.track = track;
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

    // prettier-ignore
    @Override
    public String toString() {
        return "MediaDTO{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", description='" + getDescription() + "'" +
            ", orderNumber=" + getOrderNumber() +
            ", track=" + getTrack() +
            "}";
    }
}
