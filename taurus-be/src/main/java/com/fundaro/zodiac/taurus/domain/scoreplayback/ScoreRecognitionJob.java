package com.fundaro.zodiac.taurus.domain.scoreplayback;

import com.fundaro.zodiac.taurus.domain.inventory.TenantAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * Optical music recognition of the pages of a part. At most one job per part is queued or running; the result is one
 * version per engine that read the pages.
 */
@Entity
@Table(name = "score_recognition_job")
public class ScoreRecognitionJob extends TenantAuditedEntity {

    @Column(name = "sheet_music_id", nullable = false, updatable = false)
    private Long sheetMusicId;

    @Column(name = "track_id", nullable = false, updatable = false)
    private Long trackId;

    /** Shared by the jobs started together with "recognize all the parts", which end with a single notification. */
    @Column(name = "batch_id", updatable = false)
    private UUID batchId;

    @Column(name = "pages_fingerprint", nullable = false, updatable = false)
    private String pagesFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ScoreRecognitionJobStatus status;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    /** Keycloak id of the user who started the recognition, notified at the end. */
    @Column(name = "requested_by", nullable = false, updatable = false)
    private String requestedBy;

    @Column(name = "started_at")
    private ZonedDateTime startedAt;

    @Column(name = "finished_at")
    private ZonedDateTime finishedAt;

    @Column(name = "error_code")
    private String errorCode;

    /** Outcome, duration and version of each engine. */
    @Column(name = "engines_json", columnDefinition = "text")
    private String enginesJson;

    public Long getSheetMusicId() {
        return sheetMusicId;
    }

    public void setSheetMusicId(Long sheetMusicId) {
        this.sheetMusicId = sheetMusicId;
    }

    public Long getTrackId() {
        return trackId;
    }

    public void setTrackId(Long trackId) {
        this.trackId = trackId;
    }

    public UUID getBatchId() {
        return batchId;
    }

    public void setBatchId(UUID batchId) {
        this.batchId = batchId;
    }

    public String getPagesFingerprint() {
        return pagesFingerprint;
    }

    public void setPagesFingerprint(String pagesFingerprint) {
        this.pagesFingerprint = pagesFingerprint;
    }

    public ScoreRecognitionJobStatus getStatus() {
        return status;
    }

    public void setStatus(ScoreRecognitionJobStatus status) {
        this.status = status;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(String requestedBy) {
        this.requestedBy = requestedBy;
    }

    public ZonedDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(ZonedDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public ZonedDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(ZonedDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getEnginesJson() {
        return enginesJson;
    }

    public void setEnginesJson(String enginesJson) {
        this.enginesJson = enginesJson;
    }
}
