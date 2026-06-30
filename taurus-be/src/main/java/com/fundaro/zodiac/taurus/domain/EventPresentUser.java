package com.fundaro.zodiac.taurus.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class EventPresentUser extends ChildrenEntities {

    @JsonProperty("arrival_time")
    private Date arrivalTime;

    @JsonProperty("last_name")
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
        if (!(o instanceof EventPresentUser)) return false;
        return super.equals(o);
    }

    @Override
    public String toString() {
        return "EventPresentUser{" +
            "index='" + getIndex() +
            "', name='" + getName() +
            "', lastName='" + lastName +
            "', arrivalTime=" + arrivalTime +
            ", note='" + note +
            "'}";
    }
}
