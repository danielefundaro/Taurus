package com.fundaro.zodiac.taurus.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Date;

@Entity
@Table(name = "calendar_event_presence")
public class CalendarEventPresence extends AuditFields {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @Column(name = "arrival_time")
    private Date arrivalTime;

    @Column(name = "note", length = 2000)
    private String note;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Users getUser() { return user; }
    public void setUser(Users user) { this.user = user; }
    public Date getArrivalTime() { return arrivalTime; }
    public void setArrivalTime(Date arrivalTime) { this.arrivalTime = arrivalTime; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
