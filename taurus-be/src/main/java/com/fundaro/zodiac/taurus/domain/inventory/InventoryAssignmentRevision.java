package com.fundaro.zodiac.taurus.domain.inventory;

import jakarta.persistence.*;
import java.time.ZonedDateTime;

@Entity
@Table(name = "inventory_assignment_revision", uniqueConstraints = @UniqueConstraint(name = "uq_inventory_assignment_revision", columnNames = {"assignment_id", "revision_number"}))
public class InventoryAssignmentRevision extends AuditedEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private InventoryAssignment assignment;
    @Column(name = "revision_number", nullable = false)
    private int revisionNumber;
    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 40)
    private InventoryRevisionReason reason;
    @Column(name = "snapshot_json", nullable = false, columnDefinition = "text")
    private String snapshotJson;
    @Column(name = "snapshot_hash", nullable = false, length = 64)
    private String snapshotHash;
    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;
    @Column(name = "created_by", nullable = false)
    private String createdBy;

    public InventoryAssignment getAssignment() { return assignment; }
    public void setAssignment(InventoryAssignment assignment) { this.assignment = assignment; }
    public int getRevisionNumber() { return revisionNumber; }
    public void setRevisionNumber(int revisionNumber) { this.revisionNumber = revisionNumber; }
    public InventoryRevisionReason getReason() { return reason; }
    public void setReason(InventoryRevisionReason reason) { this.reason = reason; }
    public String getSnapshotJson() { return snapshotJson; }
    public void setSnapshotJson(String snapshotJson) { this.snapshotJson = snapshotJson; }
    public String getSnapshotHash() { return snapshotHash; }
    public void setSnapshotHash(String snapshotHash) { this.snapshotHash = snapshotHash; }
    public ZonedDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(ZonedDateTime createdAt) { this.createdAt = createdAt; }
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }
}
