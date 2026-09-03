package com.fundaro.zodiac.taurus.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Date;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "calendar_event_availability")
@SQLDelete(sql = "UPDATE calendar_event_availability SET deleted = true WHERE id = ? AND deleted = false")
@SQLRestriction("deleted = false")
public class CalendarEventAvailability extends AuditFields {
    public enum Availability { AVAILABLE, UNAVAILABLE }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @Enumerated(EnumType.STRING)
    @Column(name = "availability", nullable = false)
    private Availability availability;

    @Column(name = "response_date", nullable = false)
    private Date responseDate;

    /** Promemoria personale in minuti: null usa il valore dell'evento, 0 disattiva. */
    @Column(name = "reminder_minutes")
    private Integer reminderMinutes;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Users getUser() { return user; }
    public void setUser(Users user) { this.user = user; }
    public Availability getAvailability() { return availability; }
    public void setAvailability(Availability availability) { this.availability = availability; }
    public Date getResponseDate() { return responseDate; }
    public void setResponseDate(Date responseDate) { this.responseDate = responseDate; }
    public Integer getReminderMinutes() { return reminderMinutes; }
    public void setReminderMinutes(Integer reminderMinutes) { this.reminderMinutes = reminderMinutes; }
}
