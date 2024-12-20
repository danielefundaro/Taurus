package com.fundaro.zodiac.taurus.service.dto;

import jakarta.validation.constraints.NotNull;

import java.time.ZonedDateTime;

/**
 * A DTO for the {@link com.fundaro.zodiac.taurus.domain.Albums} entity.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
public class AlbumsDTO extends CommonFieldsDTO {

    @NotNull(message = "must not be null")
    private String name;

    private String description;

    private ZonedDateTime date;

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

    public ZonedDateTime getDate() {
        return date;
    }

    public void setDate(ZonedDateTime date) {
        this.date = date;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AlbumsDTO)) {
            return false;
        }

        return super.equals(o);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "AlbumsDTO{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", description='" + getDescription() + "'" +
            ", date='" + getDate() + "'" +
            "}";
    }
}
