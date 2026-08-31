package com.fundaro.zodiac.taurus.domain;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fundaro.zodiac.taurus.domain.enumeration.MediaAssetStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Entity
@Table(name = "media_asset")
public class Media extends CommonFieldsOpenSearch {

    @Column(name = "storage_key", nullable = false, length = 2048, unique = true)
    private String storageKey;

    @Column(name = "original_filename", nullable = false, length = 500)
    private String originalFilename;

    @Column(name = "mime_type", nullable = false)
    private String mimeType;

    @Column(name = "file_extension", nullable = false, length = 32)
    private String fileExtension;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private MediaAssetStatus status;

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getFileExtension() {
        return fileExtension;
    }

    public void setFileExtension(String fileExtension) {
        this.fileExtension = fileExtension;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getSha256() {
        return sha256;
    }

    public void setSha256(String sha256) {
        this.sha256 = sha256;
    }

    public MediaAssetStatus getStatus() {
        return status;
    }

    public void setStatus(MediaAssetStatus status) {
        this.status = status;
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof Media)) {
            return false;
        }

        return getId() != null && getId().equals(((Media) o).getId());
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Media{" +
            "id=" + getId() +
            ", storageKey='" + getStorageKey() + "'" +
            ", originalFilename='" + getOriginalFilename() + "'" +
            ", mimeType='" + getMimeType() + "'" +
            ", fileSize=" + getFileSize() +
            ", status=" + getStatus() +
            "}";
    }
}
