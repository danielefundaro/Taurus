package com.fundaro.zodiac.taurus.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A Tracks.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@Entity
@Table(name = "track")
public class Tracks extends StateFieldsOpenSearch {

    @JsonProperty("sub_name")
    @Column(name = "sub_name")
    private String subName;

    @Column(name = "composer")
    private String composer;

    @Column(name = "arranger")
    private String arranger;

    @Column(name = "tempo")
    private String tempo;

    @Column(name = "tone")
    private String tone;

    @ElementCollection
    @CollectionTable(name = "track_type", joinColumns = @JoinColumn(name = "track_id"))
    @Column(name = "type")
    private Set<String> type = new LinkedHashSet<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "track_id", nullable = false)
    @OrderColumn(name = "display_order")
    private List<SheetsMusic> scores = new ArrayList<>();

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

    public List<SheetsMusic> getScores() {
        return scores;
    }

    public void setScores(List<SheetsMusic> scores) {
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
            ", state='" + getState() + "'" +
            ", type=" + getType() +
            ", scores=" + getScores() +
            "}";
    }
}
