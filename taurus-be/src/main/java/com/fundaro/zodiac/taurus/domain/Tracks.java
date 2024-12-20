package com.fundaro.zodiac.taurus.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * A Tracks.
 */
@Table("tracks")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Tracks extends CommonFields {

    @NotNull(message = "must not be null")
    @Column("name")
    private String name;

    @Column("description")
    private String description;

    @Column("composer")
    private String composer;

    @Column("arranger")
    private String arranger;

    @org.springframework.data.annotation.Transient
    @JsonIgnoreProperties(value = {"album", "track"}, allowSetters = true)
    private Set<Collections> collections = new HashSet<>();

    @org.springframework.data.annotation.Transient
    @JsonIgnoreProperties(value = {"pieces", "instrument", "track"}, allowSetters = true)
    private Set<Media> media = new HashSet<>();

    @org.springframework.data.annotation.Transient
    @JsonIgnoreProperties(value = {"tracks"}, allowSetters = true)
    private LkTrackType type;

    @Column("type_id")
    private Long typeId;

    public Tracks() {
        super();
    }

    public Tracks(CommonFields commonFields) {
        super(commonFields);
    }

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Tracks id(Long id) {
        this.setId(id);
        return this;
    }

    public Tracks deleted(Boolean deleted) {
        this.setDeleted(deleted);
        return this;
    }

    public Tracks insertBy(String insertBy) {
        this.setInsertBy(insertBy);
        return this;
    }

    public Tracks insertDate(ZonedDateTime insertDate) {
        this.setInsertDate(insertDate);
        return this;
    }

    public Tracks editBy(String editBy) {
        this.setEditBy(editBy);
        return this;
    }

    public Tracks editDate(ZonedDateTime editDate) {
        this.setEditDate(editDate);
        return this;
    }

    public String getName() {
        return this.name;
    }

    public Tracks name(String name) {
        this.setName(name);
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return this.description;
    }

    public Tracks description(String description) {
        this.setDescription(description);
        return this;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getComposer() {
        return this.composer;
    }

    public Tracks composer(String composer) {
        this.setComposer(composer);
        return this;
    }

    public void setComposer(String composer) {
        this.composer = composer;
    }

    public String getArranger() {
        return this.arranger;
    }

    public Tracks arranger(String arranger) {
        this.setArranger(arranger);
        return this;
    }

    public void setArranger(String arranger) {
        this.arranger = arranger;
    }

    public Set<Collections> getCollections() {
        return this.collections;
    }

    public void setCollections(Set<Collections> collections) {
        if (this.collections != null) {
            this.collections.forEach(i -> i.setTrack(null));
        }
        if (collections != null) {
            collections.forEach(i -> i.setTrack(this));
        }
        this.collections = collections;
    }

    public Tracks collections(Set<Collections> collections) {
        this.setCollections(collections);
        return this;
    }

    public Tracks addCollection(Collections collections) {
        this.collections.add(collections);
        collections.setTrack(this);
        return this;
    }

    public Tracks removeCollection(Collections collections) {
        this.collections.remove(collections);
        collections.setTrack(null);
        return this;
    }

    public Set<Media> getMedia() {
        return this.media;
    }

    public void setMedia(Set<Media> media) {
        if (this.media != null) {
            this.media.forEach(i -> i.setTrack(null));
        }
        if (media != null) {
            media.forEach(i -> i.setTrack(this));
        }
        this.media = media;
    }

    public Tracks media(Set<Media> media) {
        this.setMedia(media);
        return this;
    }

    public Tracks addMedia(Media media) {
        this.media.add(media);
        media.setTrack(this);
        return this;
    }

    public Tracks removeMedia(Media media) {
        this.media.remove(media);
        media.setTrack(null);
        return this;
    }

    public LkTrackType getType() {
        return this.type;
    }

    public void setType(LkTrackType lkTrackType) {
        this.type = lkTrackType;
        this.typeId = lkTrackType != null ? lkTrackType.getId() : null;
    }

    public Tracks type(LkTrackType lkTrackType) {
        this.setType(lkTrackType);
        return this;
    }

    public Long getTypeId() {
        return this.typeId;
    }

    public void setTypeId(Long lkTrackType) {
        this.typeId = lkTrackType;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Tracks)) {
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
        return "Tracks{" +
            "id=" + getId() +
            ", deleted='" + getDeleted() + "'" +
            ", insertBy='" + getInsertBy() + "'" +
            ", insertDate='" + getInsertDate() + "'" +
            ", editBy='" + getEditBy() + "'" +
            ", editDate='" + getEditDate() + "'" +
            ", name='" + getName() + "'" +
            ", description='" + getDescription() + "'" +
            ", composer='" + getComposer() + "'" +
            ", arranger='" + getArranger() + "'" +
            "}";
    }
}
