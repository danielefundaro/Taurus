package com.fundaro.zodiac.taurus.domain.eventpreparation;

import com.fundaro.zodiac.taurus.domain.CalendarEvents;
import com.fundaro.zodiac.taurus.domain.Tracks;
import com.fundaro.zodiac.taurus.domain.inventory.TenantAuditedEntity;
import jakarta.persistence.*;

@Entity
@Table(name = "calendar_event_program_entry")
public class CalendarEventProgramEntry extends TenantAuditedEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "event_id", nullable = false)
    private CalendarEvents event;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "track_id", nullable = false)
    private Tracks track;
    @Column(name = "display_order", nullable = false) private int displayOrder;
    @Column(name = "notes", length = 2000) private String notes;
    @Column(name = "planned_duration_seconds") private Integer plannedDurationSeconds;
    public CalendarEvents getEvent() { return event; }
    public void setEvent(CalendarEvents value) { event = value; }
    public Tracks getTrack() { return track; }
    public void setTrack(Tracks value) { track = value; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int value) { displayOrder = value; }
    public String getNotes() { return notes; }
    public void setNotes(String value) { notes = value; }
    public Integer getPlannedDurationSeconds() { return plannedDurationSeconds; }
    public void setPlannedDurationSeconds(Integer value) { plannedDurationSeconds = value; }
}
