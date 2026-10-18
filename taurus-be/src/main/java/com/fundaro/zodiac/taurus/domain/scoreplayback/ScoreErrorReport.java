package com.fundaro.zodiac.taurus.domain.scoreplayback;

import com.fundaro.zodiac.taurus.domain.inventory.TenantAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.ZonedDateTime;

/**
 * An error heard while listening to a part. It stays open until an archivist or administrator closes it, or until another
 * version of the part is approved.
 */
@Entity
@Table(name = "score_error_report")
public class ScoreErrorReport extends TenantAuditedEntity {

    @Column(name = "sheet_music_id", nullable = false, updatable = false)
    private Long sheetMusicId;

    @Column(name = "track_id", nullable = false, updatable = false)
    private Long trackId;

    /** Version played for the part when the error was reported. */
    @Column(name = "score_version_id", nullable = false, updatable = false)
    private Long scoreVersionId;

    /** Number of the measure as written in the score; {@code null} when the listener did not indicate it. */
    @Column(name = "measure_number", updatable = false)
    private String measureNumber;

    @Column(name = "message", nullable = false, updatable = false)
    private String message;

    /** Keycloak id of the listener. */
    @Column(name = "reported_by", nullable = false, updatable = false)
    private String reportedBy;

    /** Name of the listener when the report was made; super administrators are not always users of the tenant. */
    @Column(name = "reporter_name", updatable = false)
    private String reporterName;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ScoreErrorReportStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "closure")
    private ScoreErrorReportClosure closure;

    @Column(name = "closed_by")
    private String closedBy;

    @Column(name = "closed_at")
    private ZonedDateTime closedAt;

    public void close(ScoreErrorReportClosure closure, String actor) {
        this.status = ScoreErrorReportStatus.CLOSED;
        this.closure = closure;
        this.closedBy = actor;
        this.closedAt = ZonedDateTime.now();
        touchAudit(actor);
    }

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

    public Long getScoreVersionId() {
        return scoreVersionId;
    }

    public void setScoreVersionId(Long scoreVersionId) {
        this.scoreVersionId = scoreVersionId;
    }

    public String getMeasureNumber() {
        return measureNumber;
    }

    public void setMeasureNumber(String measureNumber) {
        this.measureNumber = measureNumber;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getReportedBy() {
        return reportedBy;
    }

    public void setReportedBy(String reportedBy) {
        this.reportedBy = reportedBy;
    }

    public String getReporterName() {
        return reporterName;
    }

    public void setReporterName(String reporterName) {
        this.reporterName = reporterName;
    }

    public ScoreErrorReportStatus getStatus() {
        return status;
    }

    public void setStatus(ScoreErrorReportStatus status) {
        this.status = status;
    }

    public ScoreErrorReportClosure getClosure() {
        return closure;
    }

    public String getClosedBy() {
        return closedBy;
    }

    public ZonedDateTime getClosedAt() {
        return closedAt;
    }
}
