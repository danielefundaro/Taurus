package com.fundaro.zodiac.taurus.domain.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "content_digest", nullable = false, length = 64)
    private String contentDigest;

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
    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    public String getContentDigest() { return contentDigest; }
    public void setContentDigest(String contentDigest) { this.contentDigest = contentDigest; }
}
