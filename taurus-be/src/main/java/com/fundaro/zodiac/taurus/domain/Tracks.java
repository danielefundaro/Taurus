package com.fundaro.zodiac.taurus.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Set;

/**
 * A Tracks.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Tracks extends CommonFieldsOpenSearch {

    @JsonProperty("sub_name")
    private String subName;

    private String composer;

    private String arranger;

    private String tempo;

    private String tone;

    private Boolean complete;

    private String state;

    private Set<String> type;

    private Set<SheetsMusic> scores;

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

    public Boolean getComplete() {
        return complete;
    }

    public void setComplete(Boolean complete) {
        this.complete = complete;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public Set<String> getType() {
        return type;
    }

    public void setType(Set<String> type) {
        this.type = type;
    }

    public Set<SheetsMusic> getScores() {
        return scores;
    }

    public void setScores(Set<SheetsMusic> scores) {
        this.scores = scores;
    }

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
            ", subName='" + getSubName() + "'" +
            ", description='" + getDescription() + "'" +
            ", composer='" + getComposer() + "'" +
            ", arranger='" + getArranger() + "'" +
            ", tempo='" + getTempo() + "'" +
            ", tone='" + getTone() + "'" +
            ", complete='" + getComplete() + "'" +
            ", state='" + getState() + "'" +
            ", type=" + getType() +
            ", scores=" + getScores() +
            "}";
    }
}
