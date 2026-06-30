package com.fundaro.zodiac.taurus.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Date;
import java.util.Objects;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class EventPresentUserDTO extends ChildrenEntitiesDTO {

    private Date arrivalTime;
    private String lastName;
    private String note;

    public Date getArrivalTime() {
        return arrivalTime;
    }

    public void setArrivalTime(Date arrivalTime) {
        this.arrivalTime = arrivalTime;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EventPresentUserDTO)) return false;
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), arrivalTime, lastName, note);
    }

    @Override
    public String toString() {
        return "EventPresentUserDTO{" +
            "index='" + getIndex() +
            "', name='" + getName() +
            "', lastName='" + lastName +
            "', arrivalTime=" + arrivalTime +
            ", note='" + note +
            "'}";
    }
}
