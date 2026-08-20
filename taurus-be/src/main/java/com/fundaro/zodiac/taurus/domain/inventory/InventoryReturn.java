package com.fundaro.zodiac.taurus.domain.inventory;

import jakarta.persistence.*;
import java.time.ZonedDateTime;

@Entity
@Table(name = "inventory_return")
public class InventoryReturn extends AuditedEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private InventoryAssignment assignment;
    @Column(name = "quantity", nullable = false)
    private int quantity;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private InventoryReturnStatus status;
    @Column(name = "requested_at", nullable = false)
    private ZonedDateTime requestedAt;
    @Column(name = "requested_by", nullable = false)
    private String requestedBy;
    @Column(name = "completed_at")
    private ZonedDateTime completedAt;
    @Column(name = "completed_by")
    private String completedBy;
    @Enumerated(EnumType.STRING)
    @Column(name = "return_condition", length = 32)
    private InventoryCondition returnCondition;
    @Column(name = "notes", length = 2000)
    private String notes;

    public InventoryAssignment getAssignment() { return assignment; }
    public void setAssignment(InventoryAssignment assignment) { this.assignment = assignment; }
    public int getQuantity() { return quantity; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
    public InventoryReturnStatus getStatus() { return status; }
    public void setStatus(InventoryReturnStatus status) { this.status = status; }
    public ZonedDateTime getRequestedAt() { return requestedAt; }
    public void setRequestedAt(ZonedDateTime requestedAt) { this.requestedAt = requestedAt; }
    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }
    public ZonedDateTime getCompletedAt() { return completedAt; }
    public void setCompletedAt(ZonedDateTime completedAt) { this.completedAt = completedAt; }
    public String getCompletedBy() { return completedBy; }
    public void setCompletedBy(String completedBy) { this.completedBy = completedBy; }
    public InventoryCondition getReturnCondition() { return returnCondition; }
    public void setReturnCondition(InventoryCondition returnCondition) { this.returnCondition = returnCondition; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
}
