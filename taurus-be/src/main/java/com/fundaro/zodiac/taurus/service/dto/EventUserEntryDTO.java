package com.fundaro.zodiac.taurus.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Date;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class EventUserEntryDTO extends ChildrenEntitiesDTO {

    private Date responseDate;
    private String lastName;

    public Date getResponseDate() {
        return responseDate;
    }

    public void setResponseDate(Date responseDate) {
        this.responseDate = responseDate;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EventUserEntryDTO)) return false;
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), responseDate, lastName);
    }

    @Override
    public String toString() {
        return "EventUserEntryDTO{" +
            "index='" + getIndex() +
            "', name='" + getName() +
            "', lastName='" + lastName +
            "', responseDate=" + responseDate +
            "}";
    }
}
