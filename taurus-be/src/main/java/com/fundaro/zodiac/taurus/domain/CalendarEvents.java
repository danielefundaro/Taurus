package com.fundaro.zodiac.taurus.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.math.BigDecimal;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.PrePersist;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Entity
@Table(name = "calendar_event")
public class CalendarEvents extends StateFieldsOpenSearch {

    @Column(name = "calendar_uid", nullable = false, unique = true, updatable = false)
    private UUID calendarUid;

    @Column(name = "calendar_sequence", nullable = false)
    private Integer calendarSequence = 0;

    @Column(name = "calendar_feed_modified_at", nullable = false)
    private Instant calendarFeedModifiedAt;

    @JsonProperty("start_date")
    @Column(name = "start_date", nullable = false)
    private Date startDate;

    @JsonProperty("end_date")
    @Column(name = "end_date", nullable = false)
    private Date endDate;

    @Column(name = "location", length = 1000)
    private String location;

    @Column(name = "fee", precision = 19, scale = 4)
    private BigDecimal fee;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "event_id", nullable = false)
    @OrderColumn(name = "display_order")
    private List<EventCost> costs = new ArrayList<>();

    @JsonProperty("available_users")
    @Transient
    private List<EventUserEntry> availableUsers;

    @JsonProperty("unavailable_users")
    @Transient
    private List<EventUserEntry> unavailableUsers;

    @JsonProperty("present_users")
    @Transient
    private List<EventPresentUser> presentUsers;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "event_id", nullable = false)
    private List<CalendarEventAvailability> availabilities = new ArrayList<>();

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "event_id", nullable = false)
    @OrderColumn(name = "display_order")
    private List<CalendarEventPresence> presences = new ArrayList<>();

    @JsonProperty("reminder_minutes")
    @Column(name = "reminder_minutes")
    private Integer reminderMinutes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "series_id")
    private CalendarEventSeries series;

    @Column(name = "original_start_date")
    private Date originalStartDate;

    @Column(name = "series_sequence")
    private Integer seriesSequence;

    @Column(name = "series_exception", nullable = false)
    private Boolean seriesException = false;

    @Column(name = "series_excluded", nullable = false)
    private Boolean seriesExcluded = false;

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

    public List<CalendarEventAvailability> getAvailabilities() { return availabilities; }
    public void setAvailabilities(List<CalendarEventAvailability> availabilities) { this.availabilities = availabilities; }
    public List<CalendarEventPresence> getPresences() { return presences; }
    public void setPresences(List<CalendarEventPresence> presences) { this.presences = presences; }

    public Integer getReminderMinutes() { return reminderMinutes; }
    public void setReminderMinutes(Integer reminderMinutes) { this.reminderMinutes = reminderMinutes; }
    public CalendarEventSeries getSeries() { return series; }
    public void setSeries(CalendarEventSeries series) { this.series = series; }
    public Date getOriginalStartDate() { return originalStartDate; }
    public void setOriginalStartDate(Date originalStartDate) { this.originalStartDate = originalStartDate; }
    public Integer getSeriesSequence() { return seriesSequence; }
    public void setSeriesSequence(Integer seriesSequence) { this.seriesSequence = seriesSequence; }
    public Boolean getSeriesException() { return seriesException; }
    public void setSeriesException(Boolean seriesException) { this.seriesException = seriesException; }
    public Boolean getSeriesExcluded() { return seriesExcluded; }
    public void setSeriesExcluded(Boolean seriesExcluded) { this.seriesExcluded = seriesExcluded; }
    public UUID getCalendarUid() { return calendarUid; }
    public void setCalendarUid(UUID calendarUid) { this.calendarUid = calendarUid; }
    public Integer getCalendarSequence() { return calendarSequence; }
    public void setCalendarSequence(Integer calendarSequence) { this.calendarSequence = calendarSequence; }
    public Instant getCalendarFeedModifiedAt() { return calendarFeedModifiedAt; }
    public void setCalendarFeedModifiedAt(Instant calendarFeedModifiedAt) { this.calendarFeedModifiedAt = calendarFeedModifiedAt; }

    @PrePersist
    void initializeCalendarIdentity() {
        if (calendarUid == null) calendarUid = UUID.randomUUID();
        if (calendarSequence == null) calendarSequence = 0;
        if (calendarFeedModifiedAt == null) calendarFeedModifiedAt = Instant.now();
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
