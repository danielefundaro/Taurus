package com.fundaro.zodiac.taurus.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * A Instruments.
 */
@Table("instruments")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Instruments extends CommonFields {

    @NotNull(message = "must not be null")
    @Column("name")
    private String name;

    @Column("description")
    private String description;

    @org.springframework.data.annotation.Transient
    @JsonIgnoreProperties(value = {"pieces", "instrument", "track"}, allowSetters = true)
    private Set<Media> media = new HashSet<>();

    public Instruments() {
        super();
    }

    public Instruments(CommonFields commonFields) {
        super(commonFields);
    }

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Instruments id(Long id) {
        this.setId(id);
        return this;
    }

    public Instruments deleted(Boolean deleted) {
        this.setDeleted(deleted);
        return this;
    }

    public Instruments insertBy(String insertBy) {
        this.setInsertBy(insertBy);
        return this;
    }

    public Instruments insertDate(ZonedDateTime insertDate) {
        this.setInsertDate(insertDate);
        return this;
    }

    public Instruments editBy(String editBy) {
        this.setEditBy(editBy);
        return this;
    }

    public Instruments editDate(ZonedDateTime editDate) {
        this.setEditDate(editDate);
        return this;
    }

    public String getName() {
        return this.name;
    }

    public Instruments name(String name) {
        this.setName(name);
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return this.description;
    }

    public Instruments description(String description) {
        this.setDescription(description);
        return this;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Set<Media> getMedia() {
        return this.media;
    }

    public void setMedia(Set<Media> media) {
        if (this.media != null) {
            this.media.forEach(i -> i.setInstrument(null));
        }
        if (media != null) {
            media.forEach(i -> i.setInstrument(this));
        }
        this.media = media;
    }

    public Instruments media(Set<Media> media) {
        this.setMedia(media);
        return this;
    }

    public Instruments addMedia(Media media) {
        this.media.add(media);
        media.setInstrument(this);
        return this;
    }

    public Instruments removeMedia(Media media) {
        this.media.remove(media);
        media.setInstrument(null);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Instruments)) {
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
        return "Instruments{" +
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
