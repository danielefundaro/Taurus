package com.fundaro.zodiac.taurus.domain.finance;

import com.fundaro.zodiac.taurus.domain.inventory.TenantAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.ZonedDateTime;

@Entity
@Table(name = "finance_notification_outbox")
public class FinanceNotificationOutbox extends TenantAuditedEntity {

    @Column(name = "event_key", nullable = false, length = 160, unique = true)
    private String eventKey;

    @Column(name = "aggregate_type", nullable = false, length = 32)
    private String aggregateType;

    @Column(name = "aggregate_id")
    private Long aggregateId;

    @Column(name = "operation", nullable = false, length = 64)
    private String operation;

    @Column(name = "title", nullable = false)
    private String title;

    @Column(name = "message", nullable = false)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 16)
    private FinanceNotificationSeverity severity = FinanceNotificationSeverity.INFO;

    @Column(name = "target_path", length = 500)
    private String targetPath;

    @Column(name = "actor_id", nullable = false)
    private String actorId;

    @Column(name = "actor_display_name", nullable = false)
    private String actorDisplayName;

    @Column(name = "recipient_roles", nullable = false)
    private String recipientRoles;

    @Column(name = "occurred_at", nullable = false)
    private ZonedDateTime occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private FinanceNotificationStatus status = FinanceNotificationStatus.PENDING;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private ZonedDateTime nextAttemptAt;

    @Column(name = "delivered_at")
    private ZonedDateTime deliveredAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    public String getEventKey() { return eventKey; }
    public void setEventKey(String eventKey) { this.eventKey = eventKey; }
    public String getAggregateType() { return aggregateType; }
    public void setAggregateType(String aggregateType) { this.aggregateType = aggregateType; }
    public Long getAggregateId() { return aggregateId; }
    public void setAggregateId(Long aggregateId) { this.aggregateId = aggregateId; }
    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
    public FinanceNotificationSeverity getSeverity() { return severity; }
    public void setSeverity(FinanceNotificationSeverity severity) { this.severity = severity; }
    public String getTargetPath() { return targetPath; }
    public void setTargetPath(String targetPath) { this.targetPath = targetPath; }
    public String getActorId() { return actorId; }
    public void setActorId(String actorId) { this.actorId = actorId; }
    public String getActorDisplayName() { return actorDisplayName; }
    public void setActorDisplayName(String actorDisplayName) { this.actorDisplayName = actorDisplayName; }
    public String getRecipientRoles() { return recipientRoles; }
    public void setRecipientRoles(String recipientRoles) { this.recipientRoles = recipientRoles; }
    public ZonedDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(ZonedDateTime occurredAt) { this.occurredAt = occurredAt; }
    public FinanceNotificationStatus getStatus() { return status; }
    public void setStatus(FinanceNotificationStatus status) { this.status = status; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public ZonedDateTime getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(ZonedDateTime nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }
    public ZonedDateTime getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(ZonedDateTime deliveredAt) { this.deliveredAt = deliveredAt; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
}
