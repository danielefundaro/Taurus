package com.fundaro.zodiac.taurus.service.dto;

import jakarta.validation.constraints.NotNull;

/**
 * A DTO for the {@link com.fundaro.zodiac.taurus.domain.Performers} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class PerformersDTO extends CommonFieldsDTO {

    private String description;

    @NotNull
    private InstrumentsDTO instrument;

    @NotNull
    private MediaDTO media;

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public InstrumentsDTO getInstrument() {
        return instrument;
    }

    public void setInstrument(InstrumentsDTO instrument) {
        this.instrument = instrument;
    }

    public MediaDTO getMedia() {
        return media;
    }

    public void setMedia(MediaDTO media) {
        this.media = media;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PerformersDTO)) {
            return false;
        }

        return super.equals(o);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "PerformersDTO{" +
            "id=" + getId() +
            ", description='" + getDescription() + "'" +
            ", instrument=" + getInstrument() +
            ", media=" + getMedia() +
            "}";
    }
}
