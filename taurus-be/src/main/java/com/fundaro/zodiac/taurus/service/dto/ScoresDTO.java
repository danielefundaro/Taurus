package com.fundaro.zodiac.taurus.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fundaro.zodiac.taurus.domain.Scores;

import java.util.Objects;
import java.util.Set;

/**
 * A DTO for the {@link Scores} entity.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ScoresDTO extends CommonFieldsOpenSearchDTO {

    Set<ChildrenEntitiesDTO> media;

    public Set<ChildrenEntitiesDTO> getMedia() {
        return media;
    }

    public void setMedia(Set<ChildrenEntitiesDTO> media) {
        this.media = media;
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
        return Objects.hash(this.getId(), this.getName(), this.getDescription(), this.getMedia().hashCode());
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "ScoresDTO{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", description='" + getDescription() + "'" +
            ", media=" + getMedia() +
            "}";
    }
}
