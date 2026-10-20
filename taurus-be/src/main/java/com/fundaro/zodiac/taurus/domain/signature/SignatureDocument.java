package com.fundaro.zodiac.taurus.domain.signature;

import com.fundaro.zodiac.taurus.domain.inventory.AuditedEntity;
import jakarta.persistence.*;

/**
 * Frozen content of an act, identified by its SHA-256 hash; once signed it never changes, it only receives the seal.
 */
@Entity
@Table(name = "signature_document")
public class SignatureDocument extends AuditedEntity {

    @Column(name = "act_id", nullable = false)
    private Long actId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private SignatureDocumentRole role;

    @Column(name = "frozen_media_asset_id")
    private Long frozenMediaAssetId;

    @Column(name = "frozen_hash", nullable = false, length = 64)
    private String frozenHash;

    @Column(name = "signed_media_asset_id")
    private Long signedMediaAssetId;

    @Column(name = "sealed_media_asset_id")
    private Long sealedMediaAssetId;

    @Column(name = "sealed_hash", length = 64)
    private String sealedHash;

    public Long getActId() {
        return actId;
    }

    public void setActId(Long actId) {
        this.actId = actId;
    }

    public SignatureDocumentRole getRole() {
        return role;
    }

    public void setRole(SignatureDocumentRole role) {
        this.role = role;
    }

    public Long getFrozenMediaAssetId() {
        return frozenMediaAssetId;
    }

    public void setFrozenMediaAssetId(Long frozenMediaAssetId) {
        this.frozenMediaAssetId = frozenMediaAssetId;
    }

    public String getFrozenHash() {
        return frozenHash;
    }

    public void setFrozenHash(String frozenHash) {
        this.frozenHash = frozenHash;
    }

    public Long getSignedMediaAssetId() {
        return signedMediaAssetId;
    }

    public void setSignedMediaAssetId(Long signedMediaAssetId) {
        this.signedMediaAssetId = signedMediaAssetId;
    }

    public Long getSealedMediaAssetId() {
        return sealedMediaAssetId;
    }

    public void setSealedMediaAssetId(Long sealedMediaAssetId) {
        this.sealedMediaAssetId = sealedMediaAssetId;
    }

    public String getSealedHash() {
        return sealedHash;
    }

    public void setSealedHash(String sealedHash) {
        this.sealedHash = sealedHash;
    }
}
