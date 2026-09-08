package com.fundaro.zodiac.taurus.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.util.Objects;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SheetsMusicDTO implements Serializable {

    private Long id;

    private String description;

    private Long order;

    private Set<ChildrenEntitiesDTO> media;

    private Set<ChildrenEntitiesDTO> instruments;

    private Boolean needsReview = Boolean.FALSE;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public Set<ChildrenEntitiesDTO> getMedia() {
        return media;
    }

    public void setMedia(Set<ChildrenEntitiesDTO> media) {
        this.media = media;
    }

    public Set<ChildrenEntitiesDTO> getInstruments() {
        return instruments;
    }

    public void setInstruments(Set<ChildrenEntitiesDTO> instruments) {
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
        if (!(o instanceof SheetsMusicDTO)) {
            return false;
        }

        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.getInstruments(), super.hashCode());
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "{" +
            "id=" + getId() +
            ", description='" + getDescription() + "'" +
            ", order=" + getOrder() +
            ", media=" + getMedia() +
            ", instruments=" + getInstruments() +
            "}";
    }
}
