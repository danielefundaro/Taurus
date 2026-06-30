package com.fundaro.zodiac.taurus.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class CalendarEvents extends StateFieldsOpenSearch {

    @JsonProperty("start_date")
    private Date startDate;

    @JsonProperty("end_date")
    private Date endDate;

    private String location;

    private Double fee;

    private List<EventCost> costs;

    @JsonProperty("available_users")
    private List<EventUserEntry> availableUsers;

    @JsonProperty("unavailable_users")
    private List<EventUserEntry> unavailableUsers;

    @JsonProperty("present_users")
    private List<EventPresentUser> presentUsers;

    public Date getStartDate() {
        return startDate;
    }

    public void setStartDate(Date startDate) {
        this.startDate = startDate;
    }

    public Date getEndDate() {
        return endDate;
    }

    public void setEndDate(Date endDate) {
        this.endDate = endDate;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public Double getFee() {
        return fee;
    }

    public void setFee(Double fee) {
        this.fee = fee;
    }

    public List<EventCost> getCosts() {
        return costs;
    }

    public void setCosts(List<EventCost> costs) {
        this.costs = costs;
    }

    public List<EventUserEntry> getAvailableUsers() {
        return availableUsers;
    }

    public void setAvailableUsers(List<EventUserEntry> availableUsers) {
        this.availableUsers = availableUsers;
    }

    public List<EventUserEntry> getUnavailableUsers() {
        return unavailableUsers;
    }

    public void setUnavailableUsers(List<EventUserEntry> unavailableUsers) {
        this.unavailableUsers = unavailableUsers;
    }

    public List<EventPresentUser> getPresentUsers() {
        return presentUsers;
    }

    public void setPresentUsers(List<EventPresentUser> presentUsers) {
        this.presentUsers = presentUsers;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CalendarEvents)) return false;
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "CalendarEvents{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", startDate='" + startDate + "'" +
            ", endDate='" + endDate + "'" +
            ", location='" + location + "'" +
            ", state='" + getState() + "'" +
            ", fee=" + fee +
            "}";
    }
}
