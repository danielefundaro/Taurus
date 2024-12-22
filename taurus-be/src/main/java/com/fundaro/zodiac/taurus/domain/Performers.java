package com.fundaro.zodiac.taurus.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.ZonedDateTime;

/**
 * A Performers.
 */
@Table("performers")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Performers extends CommonFields {

    @Column("description")
    private String description;

    @org.springframework.data.annotation.Transient
    @JsonIgnoreProperties(value = {"performers"}, allowSetters = true)
    private Instruments instrument;

    @org.springframework.data.annotation.Transient
    @JsonIgnoreProperties(value = {"performers", "pieces", "track"}, allowSetters = true)
    private Media media;

    @Column("instrument_id")
    private Long instrumentId;

    @Column("media_id")
    private Long mediaId;

    public Performers() {
        super();
    }

    public Performers(CommonFields commonFields) {
        super(commonFields);
    }

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Performers id(Long id) {
        this.setId(id);
        return this;
    }

    public Performers deleted(Boolean deleted) {
        this.setDeleted(deleted);
        return this;
    }

    public Performers insertBy(String insertBy) {
        this.setInsertBy(insertBy);
        return this;
    }

    public Performers insertDate(ZonedDateTime insertDate) {
        this.setInsertDate(insertDate);
        return this;
    }

    public Performers editBy(String editBy) {
        this.setEditBy(editBy);
        return this;
    }

    public Performers editDate(ZonedDateTime editDate) {
        this.setEditDate(editDate);
        return this;
    }

    public String getDescription() {
        return this.description;
    }

    public Performers description(String description) {
        this.setDescription(description);
        return this;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instruments getInstrument() {
        return this.instrument;
    }

    public void setInstrument(Instruments instruments) {
        this.instrument = instruments;
        this.instrumentId = instruments != null ? instruments.getId() : null;
    }

    public Performers instrument(Instruments instruments) {
        this.setInstrument(instruments);
        return this;
    }

    public Media getMedia() {
        return this.media;
    }

    public void setMedia(Media media) {
        this.media = media;
        this.mediaId = media != null ? media.getId() : null;
    }

    public Performers media(Media media) {
        this.setMedia(media);
        return this;
    }

    public Long getInstrumentId() {
        return this.instrumentId;
    }

    public void setInstrumentId(Long instruments) {
        this.instrumentId = instruments;
    }

    public Long getMediaId() {
        return this.mediaId;
    }

    public void setMediaId(Long media) {
        this.mediaId = media;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Performers)) {
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
        return "Performers{" +
            "id=" + getId() +
            ", description='" + getDescription() + "'" +
            "}";
    }
}
