package com.fundaro.zodiac.taurus.service.dto;

import jakarta.validation.constraints.NotNull;

/**
 * A DTO for the {@link com.fundaro.zodiac.taurus.domain.LkTrackType} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class LkTrackTypeDTO extends CommonFieldsDTO {

    @NotNull(message = "must not be null")
    private String name;

    private String description;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LkTrackTypeDTO)) {
            return false;
        }

        return super.equals(o);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "LkTrackTypeDTO{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", description='" + getDescription() + "'" +
            "}";
    }
}
