package com.fundaro.zodiac.taurus.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Set;

/**
 * A Scores.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Scores extends CommonFieldsOpenSearch {

    Set<ChildrenEntities> media;

    Set<ChildrenEntities> instruments;

    public Set<ChildrenEntities> getMedia() {
        return media;
    }

    public void setMedia(Set<ChildrenEntities> media) {
        this.media = media;
    }

    public Set<ChildrenEntities> getInstruments() {
        return instruments;
    }

    public void setInstruments(Set<ChildrenEntities> instruments) {
        this.instruments = instruments;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Scores)) {
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
        return "Scores{" +
            "id=" + getId() +
            ", deleted='" + getDeleted() + "'" +
            ", insertBy='" + getInsertBy() + "'" +
            ", insertDate='" + getInsertDate() + "'" +
            ", editBy='" + getEditBy() + "'" +
            ", editDate='" + getEditDate() + "'" +
            ", name='" + getName() + "'" +
            ", description='" + getDescription() + "'" +
            ", media=" + getMedia() +
            ", instruments=" + getInstruments() +
            "}";
    }
}
