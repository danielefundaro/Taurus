package com.fundaro.zodiac.taurus.domain.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.ZonedDateTime;

@Entity
@Table(
    name = "inventory_expiration_notice",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_inventory_expiration_notice",
        columnNames = { "assignment_id", "expiration_date", "notice_type" }
    )
)
public class InventoryExpirationNotice extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private InventoryAssignment assignment;

    @Column(name = "expiration_date", nullable = false)
    private LocalDate expirationDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "notice_type", nullable = false, length = 32)
    private InventoryExpirationNoticeType noticeType;

    @Column(name = "delivered_at", nullable = false)
    private ZonedDateTime deliveredAt;

    public InventoryAssignment getAssignment() { return assignment; }
    public void setAssignment(InventoryAssignment assignment) { this.assignment = assignment; }
    public LocalDate getExpirationDate() { return expirationDate; }
    public void setExpirationDate(LocalDate expirationDate) { this.expirationDate = expirationDate; }
    public InventoryExpirationNoticeType getNoticeType() { return noticeType; }
    public void setNoticeType(InventoryExpirationNoticeType noticeType) { this.noticeType = noticeType; }
    public ZonedDateTime getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(ZonedDateTime deliveredAt) { this.deliveredAt = deliveredAt; }
}
