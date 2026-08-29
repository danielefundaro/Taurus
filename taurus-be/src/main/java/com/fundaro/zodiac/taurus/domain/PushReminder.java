package com.fundaro.zodiac.taurus.domain;

import jakarta.persistence.*;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

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
}
