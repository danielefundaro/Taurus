package com.fundaro.zodiac.taurus.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "track_page_edit_receipt", uniqueConstraints = @UniqueConstraint(name = "uq_track_page_edit_receipt_actor_key", columnNames = {"requested_by", "request_key"}))
public class TrackPageEditReceipt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_key", nullable = false, updatable = false)
    private UUID requestKey;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private String requestedBy;

    @Column(name = "request_fingerprint", nullable = false, length = 64, updatable = false)
    private String requestFingerprint;

    @Column(name = "track_id", nullable = false, updatable = false)
    private Long trackId;

    @Column(name = "sheet_music_id", nullable = false, updatable = false)
    private Long sheetMusicId;

    @Column(name = "source_media_id", nullable = false, updatable = false)
    private Long sourceMediaId;

    @Column(name = "result_track_version", nullable = false, updatable = false)
    private Long resultTrackVersion;

    @Column(name = "result_media_json", nullable = false, columnDefinition = "TEXT", updatable = false)
    private String resultMediaJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private ZonedDateTime createdAt = ZonedDateTime.now();

    public Long getId() { return id; }
    public UUID getRequestKey() { return requestKey; }
    public void setRequestKey(UUID requestKey) { this.requestKey = requestKey; }
    public String getRequestedBy() { return requestedBy; }
    public void setRequestedBy(String requestedBy) { this.requestedBy = requestedBy; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public void setRequestFingerprint(String requestFingerprint) { this.requestFingerprint = requestFingerprint; }
    public Long getTrackId() { return trackId; }
    public void setTrackId(Long trackId) { this.trackId = trackId; }
    public Long getSheetMusicId() { return sheetMusicId; }
    public void setSheetMusicId(Long sheetMusicId) { this.sheetMusicId = sheetMusicId; }
    public Long getSourceMediaId() { return sourceMediaId; }
    public void setSourceMediaId(Long sourceMediaId) { this.sourceMediaId = sourceMediaId; }
    public Long getResultTrackVersion() { return resultTrackVersion; }
    public void setResultTrackVersion(Long resultTrackVersion) { this.resultTrackVersion = resultTrackVersion; }
    public String getResultMediaJson() { return resultMediaJson; }
    public void setResultMediaJson(String resultMediaJson) { this.resultMediaJson = resultMediaJson; }
    public ZonedDateTime getCreatedAt() { return createdAt; }
}
