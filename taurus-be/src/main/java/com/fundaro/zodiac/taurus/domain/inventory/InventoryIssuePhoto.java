package com.fundaro.zodiac.taurus.domain.inventory;

import com.fundaro.zodiac.taurus.domain.Media;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "inventory_issue_photo")
public class InventoryIssuePhoto extends AuditedEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "issue_id", nullable = false)
    private InventoryIssueReport issue;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_asset_id", nullable = false)
    private Media mediaAsset;
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public InventoryIssueReport getIssue() { return issue; }
    public void setIssue(InventoryIssueReport value) { issue = value; }
    public Media getMediaAsset() { return mediaAsset; }
    public void setMediaAsset(Media value) { mediaAsset = value; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int value) { displayOrder = value; }
}
