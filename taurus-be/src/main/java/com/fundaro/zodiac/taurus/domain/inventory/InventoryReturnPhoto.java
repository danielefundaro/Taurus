package com.fundaro.zodiac.taurus.domain.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "inventory_return_photo")
public class InventoryReturnPhoto extends AuditedEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "return_id", nullable = false) private InventoryReturn inventoryReturn;
    @Column(name = "file_name", nullable = false) private String fileName;
    @Column(name = "content_type", nullable = false, length = 64) private String contentType;
    @Column(name = "storage_path", nullable = false, length = 2048) private String storagePath;
    @Column(name = "content_digest", nullable = false, length = 64) private String contentDigest;
    @Column(name = "file_size", nullable = false) private long fileSize;
    public InventoryReturn getInventoryReturn() { return inventoryReturn; }
    public void setInventoryReturn(InventoryReturn inventoryReturn) { this.inventoryReturn = inventoryReturn; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }
    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }
    public String getContentDigest() { return contentDigest; }
    public void setContentDigest(String contentDigest) { this.contentDigest = contentDigest; }
    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
}
