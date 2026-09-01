package com.fundaro.zodiac.taurus.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fundaro.zodiac.taurus.domain.CalendarEvents;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * A DTO for the {@link CalendarEvents} entity.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CalendarEventsDTO extends StateFieldOpenSearchDTO {

    private Date startDate;
    private Date endDate;
    private String location;
    private BigDecimal fee;
    private List<EventCostDTO> costs;
    private List<EventUserEntryDTO> availableUsers;
    private List<EventUserEntryDTO> unavailableUsers;
    private List<EventPresentUserDTO> presentUsers;
    private Integer reminderMinutes;
    private Long seriesId;
    private Date originalStartDate;
    private Integer seriesSequence;
    private Boolean seriesException;

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

    public BigDecimal getFee() {
        return fee;
    }

    public void setFee(BigDecimal fee) {
        this.fee = fee;
    }

    public List<EventCostDTO> getCosts() {
        return costs;
    }

    public void setCosts(List<EventCostDTO> costs) {
        this.costs = costs;
    }

    public List<EventUserEntryDTO> getAvailableUsers() {
        return availableUsers;
    }

    public void setAvailableUsers(List<EventUserEntryDTO> availableUsers) {
        this.availableUsers = availableUsers;
    }

    public List<EventUserEntryDTO> getUnavailableUsers() {
        return unavailableUsers;
    }

    public void setUnavailableUsers(List<EventUserEntryDTO> unavailableUsers) {
        this.unavailableUsers = unavailableUsers;
    }

    public List<EventPresentUserDTO> getPresentUsers() {
        return presentUsers;
    }

    public void setPresentUsers(List<EventPresentUserDTO> presentUsers) {
        this.presentUsers = presentUsers;
    }

    public Integer getReminderMinutes() { return reminderMinutes; }
    public void setReminderMinutes(Integer reminderMinutes) { this.reminderMinutes = reminderMinutes; }
    public Long getSeriesId() { return seriesId; }
    public void setSeriesId(Long seriesId) { this.seriesId = seriesId; }
    public Date getOriginalStartDate() { return originalStartDate; }
    public void setOriginalStartDate(Date originalStartDate) { this.originalStartDate = originalStartDate; }
    public Integer getSeriesSequence() { return seriesSequence; }
    public void setSeriesSequence(Integer seriesSequence) { this.seriesSequence = seriesSequence; }
    public Boolean getSeriesException() { return seriesException; }
    public void setSeriesException(Boolean seriesException) { this.seriesException = seriesException; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CalendarEventsDTO)) return false;
        return super.equals(o);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), startDate, location, fee);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "CalendarEventsDTO{" +
            "id='" + getId() + "'" +
            ", name='" + getName() + "'" +
            ", startDate=" + startDate +
            ", endDate=" + endDate +
            ", location='" + location + "'" +
            ", state='" + getState() + "'" +
            ", fee=" + fee +
            "}";
    }
}
