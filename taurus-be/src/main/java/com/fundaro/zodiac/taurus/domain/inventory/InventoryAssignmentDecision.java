package com.fundaro.zodiac.taurus.domain.inventory;

import jakarta.persistence.*;
import java.time.ZonedDateTime;

@Entity
@Table(name = "inventory_assignment_decision", uniqueConstraints = @UniqueConstraint(name = "uq_inventory_revision_decision", columnNames = "revision_id"))
public class InventoryAssignmentDecision extends AuditedEntity {
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "revision_id", nullable = false)
    private InventoryAssignmentRevision revision;
    @Enumerated(EnumType.STRING)
    @Column(name = "decision", nullable = false, length = 16)
    private InventoryDecisionType decision;
    @Column(name = "rejection_reason", length = 2000)
    private String rejectionReason;
    @Column(name = "decided_at", nullable = false)
    private ZonedDateTime decidedAt;
    @Column(name = "decided_by", nullable = false)
    private String decidedBy;
    @Column(name = "authenticated_hash", nullable = false, length = 64)
    private String authenticatedHash;

    public InventoryAssignmentRevision getRevision() { return revision; }
    public void setRevision(InventoryAssignmentRevision revision) { this.revision = revision; }
    public InventoryDecisionType getDecision() { return decision; }
    public void setDecision(InventoryDecisionType decision) { this.decision = decision; }
    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }
    public ZonedDateTime getDecidedAt() { return decidedAt; }
    public void setDecidedAt(ZonedDateTime decidedAt) { this.decidedAt = decidedAt; }
    public String getDecidedBy() { return decidedBy; }
    public void setDecidedBy(String decidedBy) { this.decidedBy = decidedBy; }
    public String getAuthenticatedHash() { return authenticatedHash; }
    public void setAuthenticatedHash(String authenticatedHash) { this.authenticatedHash = authenticatedHash; }
}
