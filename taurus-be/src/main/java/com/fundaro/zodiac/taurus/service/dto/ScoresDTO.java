package com.fundaro.zodiac.taurus.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fundaro.zodiac.taurus.domain.ChildrenEntities;
import com.fundaro.zodiac.taurus.domain.Scores;

import java.util.Objects;
import java.util.Set;

/**
 * A DTO for the {@link Scores} entity.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScoresDTO extends CommonFieldsOpenSearchDTO {

    Set<ChildrenEntitiesDTO> media;

    Set<ChildrenEntities> instruments;

    public Set<ChildrenEntitiesDTO> getMedia() {
        return media;
    }

    public void setMedia(Set<ChildrenEntitiesDTO> media) {
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
        if (!(o instanceof ScoresDTO)) {
            return false;
        }

        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getId(), this.getName(), this.getDescription(), this.getMedia().hashCode(), this.getInstruments().hashCode());
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "ScoresDTO{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", description='" + getDescription() + "'" +
            ", media=" + getMedia() +
            ", instruments=" + getInstruments() +
            "}";
    }
}
