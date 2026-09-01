package com.fundaro.zodiac.taurus.domain;

import com.fundaro.zodiac.taurus.domain.enumeration.RecurrenceEndType;
import com.fundaro.zodiac.taurus.domain.enumeration.RecurrenceFrequency;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "calendar_event_series")
public class CalendarEventSeries extends StateFieldsOpenSearch {

    @Column(name = "location", length = 1000)
    private String location;

    @Column(name = "fee", precision = 19, scale = 4)
    private BigDecimal fee;

    @Column(name = "reminder_minutes")
    private Integer reminderMinutes;

    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone;

    @Column(name = "first_start_local", nullable = false)
    private LocalDateTime firstStartLocal;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false, length = 16)
    private RecurrenceFrequency frequency;

    @Column(name = "interval_value", nullable = false)
    private Integer intervalValue;

    @Column(name = "week_days", length = 32)
    private String weekDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "end_type", nullable = false, length = 16)
    private RecurrenceEndType endType;

    @Column(name = "occurrence_count")
    private Integer occurrenceCount;

    @Column(name = "until_local_date")
    private LocalDate untilLocalDate;

    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "series_id", nullable = false)
    @OrderColumn(name = "display_order")
    private List<CalendarEventSeriesCost> costs = new ArrayList<>();

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public BigDecimal getFee() { return fee; }
    public void setFee(BigDecimal fee) { this.fee = fee; }
    public Integer getReminderMinutes() { return reminderMinutes; }
    public void setReminderMinutes(Integer reminderMinutes) { this.reminderMinutes = reminderMinutes; }
    public String getTimeZone() { return timeZone; }
    public void setTimeZone(String timeZone) { this.timeZone = timeZone; }
    public LocalDateTime getFirstStartLocal() { return firstStartLocal; }
    public void setFirstStartLocal(LocalDateTime firstStartLocal) { this.firstStartLocal = firstStartLocal; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }
    public RecurrenceFrequency getFrequency() { return frequency; }
    public void setFrequency(RecurrenceFrequency frequency) { this.frequency = frequency; }
    public Integer getIntervalValue() { return intervalValue; }
    public void setIntervalValue(Integer intervalValue) { this.intervalValue = intervalValue; }
    public String getWeekDays() { return weekDays; }
    public void setWeekDays(String weekDays) { this.weekDays = weekDays; }
    public RecurrenceEndType getEndType() { return endType; }
    public void setEndType(RecurrenceEndType endType) { this.endType = endType; }
    public Integer getOccurrenceCount() { return occurrenceCount; }
    public void setOccurrenceCount(Integer occurrenceCount) { this.occurrenceCount = occurrenceCount; }
    public LocalDate getUntilLocalDate() { return untilLocalDate; }
    public void setUntilLocalDate(LocalDate untilLocalDate) { this.untilLocalDate = untilLocalDate; }
    public List<CalendarEventSeriesCost> getCosts() { return costs; }
    public void setCosts(List<CalendarEventSeriesCost> costs) { this.costs = costs; }
}
