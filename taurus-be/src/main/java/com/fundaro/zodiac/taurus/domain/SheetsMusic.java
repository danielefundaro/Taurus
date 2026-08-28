package com.fundaro.zodiac.taurus.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Entity
@Table(name = "sheet_music")
public class SheetsMusic implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Transient
    private Long order;

    @ManyToMany
    @JoinTable(name = "sheet_music_media", joinColumns = @JoinColumn(name = "sheet_music_id"), inverseJoinColumns = @JoinColumn(name = "media_id"))
    @OrderColumn(name = "display_order")
    private List<Media> media = new ArrayList<>();

    @ManyToMany
    @JoinTable(name = "sheet_music_instrument", joinColumns = @JoinColumn(name = "sheet_music_id"), inverseJoinColumns = @JoinColumn(name = "instrument_id"))
    @OrderColumn(name = "display_order")
    private List<Instruments> instruments = new ArrayList<>();

    @Column(name = "needs_review", nullable = false)
    private Boolean needsReview = Boolean.FALSE;

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getOrder() {
        return order;
    }

    public void setOrder(Long order) {
        this.order = order;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public List<Media> getMedia() {
        return media;
    }

    public void setMedia(List<Media> media) {
        this.media = media;
    }

    public List<Instruments> getInstruments() {
        return instruments;
    }

    public void setInstruments(List<Instruments> instruments) {
        this.instruments = instruments;
    }

    public Boolean getNeedsReview() {
        return needsReview;
    }

    public void setNeedsReview(Boolean needsReview) {
        this.needsReview = Boolean.TRUE.equals(needsReview);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SheetsMusic)) {
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
        return "{" +
            "description='" + getDescription() + "'" +
            ", order=" + getOrder() +
            ", media=" + getMedia() +
            ", instruments=" + getInstruments() +
            "}";
    }
}
