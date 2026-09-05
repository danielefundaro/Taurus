package com.fundaro.zodiac.taurus.domain;

import jakarta.persistence.*;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import com.fundaro.zodiac.taurus.domain.notification.NotificationStatus;
import com.fundaro.zodiac.taurus.domain.notification.ReminderOrigin;
import java.time.ZonedDateTime;

@Entity
@Table(name = "push_reminders")
public class PushReminder extends AuditFields implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "event_name")
    private String eventName;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "send_at", nullable = false)
    private Instant sendAt;

    @Column(name = "sent", nullable = false)
    private boolean sent = false;

    @Column(name = "event_start_at")
    private Instant eventStartAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private NotificationStatus status = NotificationStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "reminder_origin", nullable = false, length = 16)
    private ReminderOrigin reminderOrigin = ReminderOrigin.APPLICATION;

    @Column(name = "schedule_revision", nullable = false)
    private int scheduleRevision;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at")
    private ZonedDateTime nextAttemptAt;

    @Column(name = "delivered_at")
    private ZonedDateTime deliveredAt;

    @Column(name = "skip_reason", length = 32)
    private String skipReason;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getEventId() { return eventId; }
    public void setEventId(Long eventId) { this.eventId = eventId; }

    public String getEventName() { return eventName; }
    public void setEventName(String eventName) { this.eventName = eventName; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Instant getSendAt() { return sendAt; }
    public void setSendAt(Instant sendAt) { this.sendAt = sendAt; }

    public boolean isSent() { return sent; }
    public void setSent(boolean sent) { this.sent = sent; }
    public Instant getEventStartAt() { return eventStartAt; }
    public void setEventStartAt(Instant value) { eventStartAt = value; }
    public NotificationStatus getStatus() { return status; }
    public void setStatus(NotificationStatus value) { status = value; }
    public ReminderOrigin getReminderOrigin() { return reminderOrigin; }
    public void setReminderOrigin(ReminderOrigin value) { reminderOrigin = value; }
    public int getScheduleRevision() { return scheduleRevision; }
    public void setScheduleRevision(int value) { scheduleRevision = value; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int value) { attempts = value; }
    public ZonedDateTime getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(ZonedDateTime value) { nextAttemptAt = value; }
    public ZonedDateTime getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(ZonedDateTime value) { deliveredAt = value; }
    public String getSkipReason() { return skipReason; }
    public void setSkipReason(String value) { skipReason = value; }
    public String getLastError() { return lastError; }
    public void setLastError(String value) { lastError = value; }
}
