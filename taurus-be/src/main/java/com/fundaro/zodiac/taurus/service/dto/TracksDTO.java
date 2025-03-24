package com.fundaro.zodiac.taurus.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;
import java.util.Set;

/**
 * A DTO for the {@link com.fundaro.zodiac.taurus.domain.Tracks} entity.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TracksDTO extends CommonFieldsOpenSearchDTO {

    private String composer;

    private String arranger;

    private Set<String> type;

    private Set<SheetsMusicDTO> scores;

    public String getComposer() {
        return composer;
    }

    public void setComposer(String composer) {
        this.composer = composer;
    }

    public String getArranger() {
        return arranger;
    }

    public void setArranger(String arranger) {
        this.arranger = arranger;
    }

    public Set<String> getType() {
        return type;
    }

    public void setType(Set<String> type) {
        this.type = type;
    }

    public Set<SheetsMusicDTO> getScores() {
        return scores;
    }

    public void setScores(Set<SheetsMusicDTO> scores) {
        this.scores = scores;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TracksDTO)) {
            return false;
        }

        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getId(), this.getName(), this.getDescription(), this.getComposer(), this.getArranger(), this.getType(), this.getScores().hashCode());
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "TracksDTO{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", description='" + getDescription() + "'" +
            ", composer='" + getComposer() + "'" +
            ", arranger='" + getArranger() + "'" +
            ", type=" + getType() +
            ", scores=" + getScores() +
            "}";
    }
}
