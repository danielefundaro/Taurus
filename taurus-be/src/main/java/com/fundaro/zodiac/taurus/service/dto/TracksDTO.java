package com.fundaro.zodiac.taurus.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Objects;
import java.util.Set;

/**
 * A DTO for the {@link com.fundaro.zodiac.taurus.domain.Tracks} entity.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TracksDTO extends StateFieldOpenSearchDTO {

    private String subName;

    private String composer;

    private String arranger;

    private String tempo;

    private String tone;

    private Set<String> type;

    private Set<SheetsMusicDTO> scores;

    public String getSubName() {
        return subName;
    }

    public void setSubName(String subName) {
        this.subName = subName;
    }

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

    public String getTempo() {
        return tempo;
    }

    public void setTempo(String tempo) {
        this.tempo = tempo;
    }

    public String getTone() {
        return tone;
    }

    public void setTone(String tone) {
        this.tone = tone;
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
        return Objects.hash(super.hashCode(), this.getId(), this.getName(), this.getDescription(), this.getComposer(), this.getArranger(), this.getTempo(), this.getTone(), this.getType(), this.getScores().hashCode());
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "TracksDTO{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", description='" + getDescription() + "'" +
            ", subName='" + getSubName() + "'" +
            ", composer='" + getComposer() + "'" +
            ", arranger='" + getArranger() + "'" +
            ", tempo='" + getTempo() + "'" +
            ", tone='" + getTone() + "'" +
            ", state='" + getState() + "'" +
            ", type=" + getType() +
            ", scores=" + getScores() +
            "}";
    }
}
