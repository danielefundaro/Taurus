package com.fundaro.zodiac.taurus.domain.inventory;

import com.fundaro.zodiac.taurus.domain.Media;
import jakarta.persistence.*;

import java.time.ZonedDateTime;

@Entity
@Table(name = "inventory_loan_sheet_export")
public class InventoryLoanSheetExport extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sheet_id", nullable = false)
    private InventoryLoanSheet sheet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_asset_id")
    private Media mediaAsset;

    @Column(name = "generated_by", nullable = false)
    private String generatedBy;

    @Column(name = "generated_at", nullable = false)
    private ZonedDateTime generatedAt;

    public InventoryLoanSheet getSheet() {
        return sheet;
    }

    public void setSheet(InventoryLoanSheet sheet) {
        this.sheet = sheet;
    }

    public Media getMediaAsset() {
        return mediaAsset;
    }

    public void setMediaAsset(Media mediaAsset) {
        this.mediaAsset = mediaAsset;
    }

    public String getGeneratedBy() {
        return generatedBy;
    }

    public void setGeneratedBy(String generatedBy) {
        this.generatedBy = generatedBy;
    }

    public ZonedDateTime getGeneratedAt() {
        return generatedAt;
    }

    public void setGeneratedAt(ZonedDateTime generatedAt) {
        this.generatedAt = generatedAt;
    }
}
