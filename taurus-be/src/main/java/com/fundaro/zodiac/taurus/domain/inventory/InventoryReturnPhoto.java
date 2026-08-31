package com.fundaro.zodiac.taurus.domain.inventory;

import com.fundaro.zodiac.taurus.domain.Media;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "inventory_return_photo")
public class InventoryReturnPhoto extends AuditedEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "return_id", nullable = false) private InventoryReturn inventoryReturn;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "media_asset_id", nullable = false) private Media mediaAsset;
    public InventoryReturn getInventoryReturn() { return inventoryReturn; }
    public void setInventoryReturn(InventoryReturn inventoryReturn) { this.inventoryReturn = inventoryReturn; }
    public Media getMediaAsset() { return mediaAsset; }
    public void setMediaAsset(Media mediaAsset) { this.mediaAsset = mediaAsset; }
}
