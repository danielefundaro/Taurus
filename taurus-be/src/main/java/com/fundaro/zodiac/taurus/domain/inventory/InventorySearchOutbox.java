package com.fundaro.zodiac.taurus.domain.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.ZonedDateTime;

@Entity
@Table(name = "inventory_search_outbox")
public class InventorySearchOutbox extends AuditedEntity {

    @Column(name = "item_id", nullable = false)
    private Long itemId;

    @Enumerated(EnumType.STRING)
    @Column(name = "operation", nullable = false, length = 16)
    private InventoryOutboxOperation operation;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private InventoryOutboxStatus status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at", nullable = false)
    private ZonedDateTime nextAttemptAt;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @Column(name = "processed_at")
    private ZonedDateTime processedAt;

    @Column(name = "last_error", length = 2000)
    private String lastError;

    public Long getItemId() { return itemId; }
    public void setItemId(Long itemId) { this.itemId = itemId; }
    public InventoryOutboxOperation getOperation() { return operation; }
    public void setOperation(InventoryOutboxOperation operation) { this.operation = operation; }
    public InventoryOutboxStatus getStatus() { return status; }
    public void setStatus(InventoryOutboxStatus status) { this.status = status; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
    public ZonedDateTime getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(ZonedDateTime nextAttemptAt) { this.nextAttemptAt = nextAttemptAt; }
    public ZonedDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(ZonedDateTime createdAt) { this.createdAt = createdAt; }
    public ZonedDateTime getProcessedAt() { return processedAt; }
    public void setProcessedAt(ZonedDateTime processedAt) { this.processedAt = processedAt; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
}
