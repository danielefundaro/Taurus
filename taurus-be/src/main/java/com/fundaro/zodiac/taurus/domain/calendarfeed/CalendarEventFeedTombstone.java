package com.fundaro.zodiac.taurus.domain.calendarfeed;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "calendar_event_feed_tombstone", uniqueConstraints = @UniqueConstraint(name = "uq_calendar_feed_tombstone", columnNames = {"event_uid", "audience"}))
public class CalendarEventFeedTombstone {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "event_uid", nullable = false) private UUID eventUid;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private CalendarFeedAudience audience;
    @Column(nullable = false) private int sequence;
    @Column(name = "original_start_date", nullable = false) private Instant originalStartDate;
    @Column(name = "original_end_date", nullable = false) private Instant originalEndDate;
    @Column(name = "summary_snapshot", nullable = false, length = 255) private String summarySnapshot;
    @Column(name = "cancelled_at", nullable = false) private Instant cancelledAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    public Long getId() { return id; }
    public UUID getEventUid() { return eventUid; } public void setEventUid(UUID v) { eventUid = v; }
    public CalendarFeedAudience getAudience() { return audience; } public void setAudience(CalendarFeedAudience v) { audience = v; }
    public int getSequence() { return sequence; } public void setSequence(int v) { sequence = v; }
    public Instant getOriginalStartDate() { return originalStartDate; } public void setOriginalStartDate(Instant v) { originalStartDate = v; }
    public Instant getOriginalEndDate() { return originalEndDate; } public void setOriginalEndDate(Instant v) { originalEndDate = v; }
    public String getSummarySnapshot() { return summarySnapshot; } public void setSummarySnapshot(String v) { summarySnapshot = v; }
    public Instant getCancelledAt() { return cancelledAt; } public void setCancelledAt(Instant v) { cancelledAt = v; }
    public Instant getExpiresAt() { return expiresAt; } public void setExpiresAt(Instant v) { expiresAt = v; }
}
