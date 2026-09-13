package com.fundaro.zodiac.taurus.domain.inventory;

import jakarta.persistence.*;

import java.time.ZonedDateTime;

@Entity
@Table(name = "inventory_issue_report")
public class InventoryIssueReport extends TenantAuditedEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private InventoryItem item;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignment_id")
    private InventoryAssignment assignment;
    @Column(name = "reported_quantity", nullable = false)
    private int reportedQuantity;
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 32)
    private InventoryIssueSeverity severity;
    @Column(name = "description", nullable = false, length = 2000)
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private InventoryIssueStatus status;
    @Column(name = "resolution_notes", length = 2000)
    private String resolutionNotes;
    @Column(name = "acknowledged_at")
    private ZonedDateTime acknowledgedAt;
    @Column(name = "acknowledged_by")
    private String acknowledgedBy;
    @Column(name = "resolved_at")
    private ZonedDateTime resolvedAt;
    @Column(name = "resolved_by")
    private String resolvedBy;

    public InventoryItem getItem() {
        return item;
    }

    public void setItem(InventoryItem value) {
        item = value;
    }

    public InventoryAssignment getAssignment() {
        return assignment;
    }

    public void setAssignment(InventoryAssignment value) {
        assignment = value;
    }

    public int getReportedQuantity() {
        return reportedQuantity;
    }

    public void setReportedQuantity(int value) {
        reportedQuantity = value;
    }

    public InventoryIssueSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(InventoryIssueSeverity value) {
        severity = value;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String value) {
        description = value;
    }

    public InventoryIssueStatus getStatus() {
        return status;
    }

    public void setStatus(InventoryIssueStatus value) {
        status = value;
    }

    public String getResolutionNotes() {
        return resolutionNotes;
    }

    public void setResolutionNotes(String value) {
        resolutionNotes = value;
    }

    public ZonedDateTime getAcknowledgedAt() {
        return acknowledgedAt;
    }

    public void setAcknowledgedAt(ZonedDateTime value) {
        acknowledgedAt = value;
    }

    public String getAcknowledgedBy() {
        return acknowledgedBy;
    }

    public void setAcknowledgedBy(String value) {
        acknowledgedBy = value;
    }

    public ZonedDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(ZonedDateTime value) {
        resolvedAt = value;
    }

    public String getResolvedBy() {
        return resolvedBy;
    }

    public void setResolvedBy(String value) {
        resolvedBy = value;
    }
}
