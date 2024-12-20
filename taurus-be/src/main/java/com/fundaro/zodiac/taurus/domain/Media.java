package com.fundaro.zodiac.taurus.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * A Media.
 */
@Table("media")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Media extends CommonFields {

    @Column("name")
    private String name;

    @Column("description")
    private String description;

    @Column("order_number")
    private Long orderNumber;

    @org.springframework.data.annotation.Transient
    @JsonIgnoreProperties(value = {"media"}, allowSetters = true)
    private Set<Pieces> pieces = new HashSet<>();

    @org.springframework.data.annotation.Transient
    @JsonIgnoreProperties(value = {"media"}, allowSetters = true)
    private Instruments instrument;

    @org.springframework.data.annotation.Transient
    @JsonIgnoreProperties(value = {"collections", "media", "type"}, allowSetters = true)
    private Tracks track;

    @Column("instrument_id")
    private Long instrumentId;

    @Column("track_id")
    private Long trackId;

    public Media() {
        super();
    }

    public Media(CommonFields commonFields) {
        super(commonFields);
    }

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Media id(Long id) {
        this.setId(id);
        return this;
    }

    public Media deleted(Boolean deleted) {
        this.setDeleted(deleted);
        return this;
    }

    public Media insertBy(String insertBy) {
        this.setInsertBy(insertBy);
        return this;
    }

    public Media insertDate(ZonedDateTime insertDate) {
        this.setInsertDate(insertDate);
        return this;
    }

    public Media editBy(String editBy) {
        this.setEditBy(editBy);
        return this;
    }

    public Media editDate(ZonedDateTime editDate) {
        this.setEditDate(editDate);
        return this;
    }

    public String getName() {
        return this.name;
    }

    public Media name(String name) {
        this.setName(name);
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return this.description;
    }

    public Media description(String description) {
        this.setDescription(description);
        return this;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getOrderNumber() {
        return this.orderNumber;
    }

    public Media orderNumber(Long orderNumber) {
        this.setOrderNumber(orderNumber);
        return this;
    }

    public void setOrderNumber(Long orderNumber) {
        this.orderNumber = orderNumber;
    }

    public Set<Pieces> getPieces() {
        return this.pieces;
    }

    public void setPieces(Set<Pieces> pieces) {
        if (this.pieces != null) {
            this.pieces.forEach(i -> i.setMedia(null));
        }
        if (pieces != null) {
            pieces.forEach(i -> i.setMedia(this));
        }
        this.pieces = pieces;
    }

    public Media pieces(Set<Pieces> pieces) {
        this.setPieces(pieces);
        return this;
    }

    public Media addPiece(Pieces pieces) {
        this.pieces.add(pieces);
        pieces.setMedia(this);
        return this;
    }

    public Media removePiece(Pieces pieces) {
        this.pieces.remove(pieces);
        pieces.setMedia(null);
        return this;
    }

    public Instruments getInstrument() {
        return this.instrument;
    }

    public void setInstrument(Instruments instruments) {
        this.instrument = instruments;
        this.instrumentId = instruments != null ? instruments.getId() : null;
    }

    public Media instrument(Instruments instruments) {
        this.setInstrument(instruments);
        return this;
    }

    public Tracks getTrack() {
        return this.track;
    }

    public void setTrack(Tracks tracks) {
        this.track = tracks;
        this.trackId = tracks != null ? tracks.getId() : null;
    }

    public Media track(Tracks tracks) {
        this.setTrack(tracks);
        return this;
    }

    public Long getInstrumentId() {
        return this.instrumentId;
    }

    public void setInstrumentId(Long instruments) {
        this.instrumentId = instruments;
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
        if (!(o instanceof Media)) {
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
        return "Media{" +
            "id=" + getId() +
            ", deleted='" + getDeleted() + "'" +
            ", insertBy='" + getInsertBy() + "'" +
            ", insertDate='" + getInsertDate() + "'" +
            ", editBy='" + getEditBy() + "'" +
            ", editDate='" + getEditDate() + "'" +
            ", name='" + getName() + "'" +
            ", description='" + getDescription() + "'" +
            ", orderNumber=" + getOrderNumber() +
            "}";
    }
}
