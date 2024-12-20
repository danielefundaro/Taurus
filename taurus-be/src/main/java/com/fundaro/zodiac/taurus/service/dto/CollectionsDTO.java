package com.fundaro.zodiac.taurus.service.dto;

import jakarta.validation.constraints.NotNull;

/**
 * A DTO for the {@link com.fundaro.zodiac.taurus.domain.Collections} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class CollectionsDTO extends CommonFieldsDTO {

    private Long orderNumber;

    @NotNull
    private AlbumsDTO album;

    @NotNull
    private TracksDTO track;

    public Long getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(Long orderNumber) {
        this.orderNumber = orderNumber;
    }

    public AlbumsDTO getAlbum() {
        return album;
    }

    public void setAlbum(AlbumsDTO album) {
        this.album = album;
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
        if (!(o instanceof CollectionsDTO)) {
            return false;
        }

        return super.equals(o);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "CollectionsDTO{" +
            "id=" + getId() +
            ", orderNumber=" + getOrderNumber() +
            ", album=" + getAlbum() +
            ", track=" + getTrack() +
            "}";
    }
}
