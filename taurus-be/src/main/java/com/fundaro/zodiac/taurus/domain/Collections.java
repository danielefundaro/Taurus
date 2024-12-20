package com.fundaro.zodiac.taurus.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.ZonedDateTime;

/**
 * A Collections.
 */
@Table("collections")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Collections extends CommonFields {

    @Column("order_number")
    private Long orderNumber;

    @org.springframework.data.annotation.Transient
    @JsonIgnoreProperties(value = {"collections"}, allowSetters = true)
    private Albums album;

    @org.springframework.data.annotation.Transient
    @JsonIgnoreProperties(value = {"collections", "media", "type"}, allowSetters = true)
    private Tracks track;

    @Column("album_id")
    private Long albumId;

    @Column("track_id")
    private Long trackId;

    public Collections() {
        super();
    }

    public Collections(CommonFields commonFields) {
        super(commonFields);
    }

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Collections id(Long id) {
        this.setId(id);
        return this;
    }

    public Collections deleted(Boolean deleted) {
        this.setDeleted(deleted);
        return this;
    }

    public Collections insertBy(String insertBy) {
        this.setInsertBy(insertBy);
        return this;
    }

    public Collections insertDate(ZonedDateTime insertDate) {
        this.setInsertDate(insertDate);
        return this;
    }

    public Collections editBy(String editBy) {
        this.setEditBy(editBy);
        return this;
    }

    public Collections editDate(ZonedDateTime editDate) {
        this.setEditDate(editDate);
        return this;
    }

    public Long getOrderNumber() {
        return this.orderNumber;
    }

    public Collections orderNumber(Long orderNumber) {
        this.setOrderNumber(orderNumber);
        return this;
    }

    public void setOrderNumber(Long orderNumber) {
        this.orderNumber = orderNumber;
    }

    public Albums getAlbum() {
        return this.album;
    }

    public void setAlbum(Albums albums) {
        this.album = albums;
        this.albumId = albums != null ? albums.getId() : null;
    }

    public Collections album(Albums albums) {
        this.setAlbum(albums);
        return this;
    }

    public Tracks getTrack() {
        return this.track;
    }

    public void setTrack(Tracks tracks) {
        this.track = tracks;
        this.trackId = tracks != null ? tracks.getId() : null;
    }

    public Collections track(Tracks tracks) {
        this.setTrack(tracks);
        return this;
    }

    public Long getAlbumId() {
        return this.albumId;
    }

    public void setAlbumId(Long albums) {
        this.albumId = albums;
    }

    public Long getTrackId() {
        return this.trackId;
    }

    public void setTrackId(Long tracks) {
        this.trackId = tracks;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Collections)) {
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
        return "Collections{" +
            "id=" + getId() +
            ", deleted='" + getDeleted() + "'" +
            ", insertBy='" + getInsertBy() + "'" +
            ", insertDate='" + getInsertDate() + "'" +
            ", editBy='" + getEditBy() + "'" +
            ", editDate='" + getEditDate() + "'" +
            ", orderNumber=" + getOrderNumber() +
            "}";
    }
}
