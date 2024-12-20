package com.fundaro.zodiac.taurus.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.ZonedDateTime;
import java.util.HashSet;
import java.util.Set;

/**
 * A Albums.
 */
@Table("albums")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Albums extends CommonFields {

    @NotNull(message = "must not be null")
    @Column("name")
    private String name;

    @Column("description")
    private String description;

    @Column("date")
    private ZonedDateTime date;

    @org.springframework.data.annotation.Transient
    @JsonIgnoreProperties(value = {"album", "track"}, allowSetters = true)
    private Set<Collections> collections = new HashSet<>();

    public Albums() {
        super();
    }

    public Albums(CommonFields commonFields) {
        super(commonFields);
    }

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Albums id(Long id) {
        this.setId(id);
        return this;
    }

    public Albums deleted(Boolean deleted) {
        this.setDeleted(deleted);
        return this;
    }

    public Albums insertBy(String insertBy) {
        this.setInsertBy(insertBy);
        return this;
    }

    public Albums insertDate(ZonedDateTime insertDate) {
        this.setInsertDate(insertDate);
        return this;
    }

    public Albums editBy(String editBy) {
        this.setEditBy(editBy);
        return this;
    }

    public Albums editDate(ZonedDateTime editDate) {
        this.setEditDate(editDate);
        return this;
    }

    public String getName() {
        return this.name;
    }

    public Albums name(String name) {
        this.setName(name);
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return this.description;
    }

    public Albums description(String description) {
        this.setDescription(description);
        return this;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public ZonedDateTime getDate() {
        return this.date;
    }

    public Albums date(ZonedDateTime date) {
        this.setDate(date);
        return this;
    }

    public void setDate(ZonedDateTime date) {
        this.date = date;
    }

    public Set<Collections> getCollections() {
        return this.collections;
    }

    public void setCollections(Set<Collections> collections) {
        if (this.collections != null) {
            this.collections.forEach(i -> i.setAlbum(null));
        }
        if (collections != null) {
            collections.forEach(i -> i.setAlbum(this));
        }
        this.collections = collections;
    }

    public Albums collections(Set<Collections> collections) {
        this.setCollections(collections);
        return this;
    }

    public Albums addCollection(Collections collections) {
        this.collections.add(collections);
        collections.setAlbum(this);
        return this;
    }

    public Albums removeCollection(Collections collections) {
        this.collections.remove(collections);
        collections.setAlbum(null);
        return this;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Albums)) {
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
        return "Albums{" +
            "id=" + getId() +
            ", deleted='" + getDeleted() + "'" +
            ", insertBy='" + getInsertBy() + "'" +
            ", insertDate='" + getInsertDate() + "'" +
            ", editBy='" + getEditBy() + "'" +
            ", editDate='" + getEditDate() + "'" +
            ", name='" + getName() + "'" +
            ", description='" + getDescription() + "'" +
            ", date='" + getDate() + "'" +
            "}";
    }
}
