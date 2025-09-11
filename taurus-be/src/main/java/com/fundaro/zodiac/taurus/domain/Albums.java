package com.fundaro.zodiac.taurus.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Date;
import java.util.Set;

/**
 * A Albums.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Albums extends CommonFieldsOpenSearch {

    private Date date;

    private String state;

    private Set<ChildrenEntities> tracks;

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

    public Set<ChildrenEntities> getTracks() {
        return tracks;
    }

    public void setTracks(Set<ChildrenEntities> tracks) {
        this.tracks = tracks;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Albums)) {
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
        return "Albums{" +
            "id=" + getId() +
            ", deleted='" + getDeleted() + "'" +
            ", insertBy='" + getInsertBy() + "'" +
            ", insertDate='" + getInsertDate() + "'" +
            ", editBy='" + getEditBy() + "'" +
            ", editDate='" + getEditDate() + "'" +
            ", name='" + getName() + "'" +
            ", description='" + getDescription() + "'" +
            ", date='" + getDate() + "'" +
            ", state='" + getState() + "'" +
            ", tracks=" + getTracks() +
            "}";
    }
}
