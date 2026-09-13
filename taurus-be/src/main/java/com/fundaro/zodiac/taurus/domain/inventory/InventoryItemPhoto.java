package com.fundaro.zodiac.taurus.domain.inventory;

import com.fundaro.zodiac.taurus.domain.Media;
import jakarta.persistence.*;

@Entity
@Table(name = "inventory_item_photo")
public class InventoryItemPhoto extends AuditedEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private InventoryItem item;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "media_asset_id", nullable = false)
    private Media mediaAsset;
    @Column(name = "display_order", nullable = false)
    private int displayOrder;
    @Column(name = "preview", nullable = false)
    private boolean preview;

    public InventoryItem getItem() {
        return item;
    }

    public void setItem(InventoryItem item) {
        this.item = item;
    }

    public Media getMediaAsset() {
        return mediaAsset;
    }

    public void setMediaAsset(Media mediaAsset) {
        this.mediaAsset = mediaAsset;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(int displayOrder) {
        this.displayOrder = displayOrder;
    }

    public boolean isPreview() {
        return preview;
    }

    public void setPreview(boolean preview) {
        this.preview = preview;
    }
}
