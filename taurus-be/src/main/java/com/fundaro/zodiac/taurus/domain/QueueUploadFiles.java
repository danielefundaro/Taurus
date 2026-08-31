package com.fundaro.zodiac.taurus.domain;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fundaro.zodiac.taurus.domain.enumeration.UploadFileStatusEnum;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

/**
 * A QueueUploadFiles.
 */
@SuppressWarnings("common-java:DuplicatedBlocks")
@Entity
@Table(name = "upload_job")
public class QueueUploadFiles extends CommonFieldsOpenSearch {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private Users user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "source_media_asset_id", nullable = false)
    private Media sourceMediaAsset;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "track_id")
    private Tracks track;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private UploadFileStatusEnum status;

    @Column(name = "type")
    private String type;

    @Transient
    public Long getUserId() {
        return user == null ? null : user.getId();
    }

    public Users getUser() { return user; }
    public void setUser(Users user) { this.user = user; }

    public void setUserId(Long userId) {
        if (userId == null) { this.user = null; return; }
        Users reference = new Users();
        reference.setId(userId);
        this.user = reference;
    }

    @Transient
    public Long getSourceMediaAssetId() {
        return sourceMediaAsset == null ? null : sourceMediaAsset.getId();
    }

    public Media getSourceMediaAsset() {
        return sourceMediaAsset;
    }

    public void setSourceMediaAsset(Media sourceMediaAsset) {
        this.sourceMediaAsset = sourceMediaAsset;
    }

    public void setSourceMediaAssetId(Long mediaAssetId) {
        if (mediaAssetId == null) { this.sourceMediaAsset = null; return; }
        Media reference = new Media();
        reference.setId(mediaAssetId);
        this.sourceMediaAsset = reference;
    }

    @Transient
    public Long getTrackId() {
        return track == null ? null : track.getId();
    }

    public Tracks getTrack() { return track; }
    public void setTrack(Tracks track) { this.track = track; }

    public void setTrackId(Long trackId) {
        if (trackId == null) { this.track = null; return; }
        Tracks reference = new Tracks();
        reference.setId(trackId);
        this.track = reference;
    }

    public UploadFileStatusEnum getStatus() {
        return this.status;
    }

    public void setStatus(UploadFileStatusEnum status) {
        this.status = status;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
// jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof QueueUploadFiles)) {
            return false;
        }

        return super.equals(o);
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "QueueUploadFiles{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", userId='" + getUserId() + "'" +
            ", sourceMediaAssetId='" + getSourceMediaAssetId() + "'" +
            ", trackId='" + getTrackId() + "'" +
            ", status='" + getStatus() + "'" +
            ", type='" + getType() + "'" +
            ", description='" + getDescription() + "'" +
            "}";
    }
}
