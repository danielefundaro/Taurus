package com.fundaro.zodiac.taurus.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * A LkTrackType.
 */
@Table("lk_track_type")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class LkTrackType extends CommonFields {

    @NotNull(message = "must not be null")
    @Column("name")
    private String name;

    @Column("description")
    private String description;

    @org.springframework.data.annotation.Transient
    @JsonIgnoreProperties(value = {"collections", "media", "type"}, allowSetters = true)
    private Set<Tracks> tracks = new HashSet<>();

    public LkTrackType() {
        super();
    }

    public LkTrackType(CommonFields commonFields) {
        super(commonFields);
    }

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public LkTrackType id(Long id) {
        this.setId(id);
        return this;
    }

    public LkTrackType deleted(Boolean deleted) {
        this.setDeleted(deleted);
        return this;
    }

    public LkTrackType insertBy(String insertBy) {
        this.setInsertBy(insertBy);
        return this;
    }

    public LkTrackType insertDate(ZonedDateTime insertDate) {
        this.setInsertDate(insertDate);
        return this;
    }

    public LkTrackType editBy(String editBy) {
        this.setEditBy(editBy);
        return this;
    }

    public LkTrackType editDate(ZonedDateTime editDate) {
        this.setEditDate(editDate);
        return this;
    }

    public String getName() {
        return this.name;
    }

    public LkTrackType name(String name) {
        this.setName(name);
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return this.description;
    }

    public LkTrackType description(String description) {
        this.setDescription(description);
        return this;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Set<Tracks> getTracks() {
        return this.tracks;
    }

    public void setTracks(Set<Tracks> tracks) {
        if (this.tracks != null) {
            this.tracks.forEach(i -> i.setType(null));
        }
        if (tracks != null) {
            tracks.forEach(i -> i.setType(this));
        }
        this.tracks = tracks;
    }

    public LkTrackType tracks(Set<Tracks> tracks) {
        this.setTracks(tracks);
        return this;
    }

    public LkTrackType addTrack(Tracks tracks) {
        this.tracks.add(tracks);
        tracks.setType(this);
        return this;
    }

    public LkTrackType removeTrack(Tracks tracks) {
        this.tracks.remove(tracks);
        tracks.setType(null);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LkTrackType)) {
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
        return "LkTrackType{" +
            "id=" + getId() +
            ", deleted='" + getDeleted() + "'" +
            ", insertBy='" + getInsertBy() + "'" +
            ", insertDate='" + getInsertDate() + "'" +
            ", editBy='" + getEditBy() + "'" +
            ", editDate='" + getEditDate() + "'" +
            ", name='" + getName() + "'" +
            ", description='" + getDescription() + "'" +
            "}";
    }
}
