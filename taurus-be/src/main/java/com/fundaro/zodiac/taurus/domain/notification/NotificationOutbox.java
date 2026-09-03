package com.fundaro.zodiac.taurus.domain.notification;

import com.fundaro.zodiac.taurus.domain.inventory.TenantAuditedEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "notification_outbox")
public class NotificationOutbox extends TenantAuditedEntity {

    @Column(name = "event_key", nullable = false, length = 160, unique = true)
    private String eventKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 32)
    private NotificationSource source;

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", length = 160)
    private String aggregateId;

    @Column(name = "operation", nullable = false, length = 64)
    private String operation;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "message", nullable = false, length = 255)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 16)
    private NotificationSeverity severity = NotificationSeverity.INFO;

    @Column(name = "target_path", length = 500)
    private String targetPath;

    @Column(name = "actor_id", nullable = false, length = 255)
    private String actorId;

    @Column(name = "actor_display_name", nullable = false, length = 255)
    private String actorDisplayName;

    @Column(name = "occurred_at", nullable = false)
    private ZonedDateTime occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private NotificationStatus status = NotificationStatus.PENDING;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private ZonedDateTime nextAttemptAt;

    @Column(name = "delivered_at")
    private ZonedDateTime deliveredAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @OneToMany(mappedBy = "event", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id")
    private List<NotificationOutboxAudience> audiences = new ArrayList<>();

    public void addAudience(NotificationOutboxAudience audience) {
        audience.setEvent(this);
        audiences.add(audience);
    }

    public String getEventKey() { return eventKey; }
    public void setEventKey(String eventKey) { this.eventKey = eventKey; }
    public NotificationSource getSource() { return source; }
    public void setSource(NotificationSource source) { this.source = source; }
    public String getAggregateType() { return aggregateType; }
    public void setAggregateType(String aggregateType) { this.aggregateType = aggregateType; }
    public String getAggregateId() { return aggregateId; }
    public void setAggregateId(String aggregateId) { this.aggregateId = aggregateId; }
    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public NotificationSeverity getSeverity() { return severity; }
    public void setSeverity(NotificationSeverity severity) { this.severity = severity; }
    public String getTargetPath() { return targetPath; }
    public void setTargetPath(String targetPath) { this.targetPath = targetPath; }
    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }
    public String getActorDisplayName() { return actorDisplayName; }
    public void setActorDisplayName(String actorDisplayName) { this.actorDisplayName = actorDisplayName; }
    public ZonedDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(ZonedDateTime occurredAt) { this.occurredAt = occurredAt; }
    public NotificationStatus getStatus() { return status; }
    public void setStatus(NotificationStatus status) { this.status = status; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public ZonedDateTime getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(ZonedDateTime nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }
    public ZonedDateTime getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(ZonedDateTime deliveredAt) { this.deliveredAt = deliveredAt; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public List<NotificationOutboxAudience> getAudiences() { return audiences; }
    public void setAudiences(List<NotificationOutboxAudience> audiences) { this.audiences = audiences; }
}
