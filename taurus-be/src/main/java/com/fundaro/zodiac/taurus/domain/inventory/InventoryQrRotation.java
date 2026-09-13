package com.fundaro.zodiac.taurus.domain.inventory;

import jakarta.persistence.*;

import java.time.ZonedDateTime;

@Entity
@Table(name = "inventory_qr_rotation")
public class InventoryQrRotation extends AuditedEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private InventoryItem item;
    @Column(name = "previous_version", nullable = false)
    private int previousVersion;
    @Column(name = "new_version", nullable = false)
    private int newVersion;
    @Column(name = "previous_code_digest", nullable = false, length = 64)
    private String previousCodeDigest;
    @Column(name = "new_code_digest", nullable = false, length = 64)
    private String newCodeDigest;
    @Column(name = "reason", nullable = false, length = 500)
    private String reason;
    @Column(name = "rotated_at", nullable = false)
    private ZonedDateTime rotatedAt;
    @Column(name = "rotated_by", nullable = false)
    private String rotatedBy;

    public InventoryItem getItem() {
        return item;
    }

    public void setItem(InventoryItem value) {
        item = value;
    }

    public int getPreviousVersion() {
        return previousVersion;
    }

    public void setPreviousVersion(int value) {
        previousVersion = value;
    }

    public int getNewVersion() {
        return newVersion;
    }

    public void setNewVersion(int value) {
        newVersion = value;
    }

    public String getPreviousCodeDigest() {
        return previousCodeDigest;
    }

    public void setPreviousCodeDigest(String value) {
        previousCodeDigest = value;
    }

    public String getNewCodeDigest() {
        return newCodeDigest;
    }

    public void setNewCodeDigest(String value) {
        newCodeDigest = value;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String value) {
        reason = value;
    }

    public ZonedDateTime getRotatedAt() {
        return rotatedAt;
    }

    public void setRotatedAt(ZonedDateTime value) {
        rotatedAt = value;
    }

    public String getRotatedBy() {
        return rotatedBy;
    }

    public void setRotatedBy(String value) {
        rotatedBy = value;
    }
}
