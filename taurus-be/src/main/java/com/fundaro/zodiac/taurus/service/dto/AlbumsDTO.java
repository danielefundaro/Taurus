package com.fundaro.zodiac.taurus.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fundaro.zodiac.taurus.domain.Albums;

import java.util.Date;
import java.util.Objects;
import java.util.Set;

/**
 * A DTO for the {@link Albums} entity.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AlbumsDTO extends CommonFieldsOpenSearchDTO {

    private Date date;

    private String state;

    private Set<ChildrenEntitiesDTO> tracks;

    public Date getDate() {
        return date;
    }

    public void setDate(Date date) {
        this.date = date;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public Set<ChildrenEntitiesDTO> getTracks() {
        return tracks;
    }

    public void setTracks(Set<ChildrenEntitiesDTO> tracks) {
        this.tracks = tracks;
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

    @Override
    public int hashCode() {
        return Objects.hash(this.getId(), this.getName(), this.getDescription(), this.getDate(), this.getTracks());
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "AlbumsDTO{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", description='" + getDescription() + "'" +
            ", date='" + getDate() + "'" +
            ", state='" + getState() + "'" +
            ", tracks=" + getTracks() +
            "}";
    }
}
