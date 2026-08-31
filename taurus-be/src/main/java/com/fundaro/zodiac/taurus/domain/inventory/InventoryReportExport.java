package com.fundaro.zodiac.taurus.domain.inventory;

import com.fundaro.zodiac.taurus.domain.Media;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.ZonedDateTime;

@Entity
@Table(name = "inventory_report_export")
public class InventoryReportExport extends AuditedEntity {

    @Column(name = "requested_user_index", nullable = false)
    private Long requestedUserIndex;

    @Column(name = "generated_by", nullable = false)
    private String generatedBy;

    @Column(name = "generated_at", nullable = false)
    private ZonedDateTime generatedAt;

    @Column(name = "include_assigned", nullable = false)
    private boolean includeAssigned;

    @Column(name = "include_returned", nullable = false)
    private boolean includeReturned;

    @Column(name = "include_photos", nullable = false)
    private boolean includePhotos;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "media_asset_id")
    private Media mediaAsset;

    public Long getRequestedUserIndex() { return requestedUserIndex; }
    public void setRequestedUserIndex(Long requestedUserIndex) { this.requestedUserIndex = requestedUserIndex; }
    public String getGeneratedBy() { return generatedBy; }
    public void setGeneratedBy(String generatedBy) { this.generatedBy = generatedBy; }
    public ZonedDateTime getGeneratedAt() { return generatedAt; }
    public void setGeneratedAt(ZonedDateTime generatedAt) { this.generatedAt = generatedAt; }
    public boolean isIncludeAssigned() { return includeAssigned; }
    public void setIncludeAssigned(boolean includeAssigned) { this.includeAssigned = includeAssigned; }
    public boolean isIncludeReturned() { return includeReturned; }
    public void setIncludeReturned(boolean includeReturned) { this.includeReturned = includeReturned; }
    public boolean isIncludePhotos() { return includePhotos; }
    public void setIncludePhotos(boolean includePhotos) { this.includePhotos = includePhotos; }
    public Media getMediaAsset() { return mediaAsset; }
    public void setMediaAsset(Media mediaAsset) { this.mediaAsset = mediaAsset; }
}
