package com.fundaro.zodiac.taurus.domain.onboarding;

import com.fundaro.zodiac.taurus.domain.Media;
import com.fundaro.zodiac.taurus.domain.inventory.TenantAuditedEntity;
import jakarta.persistence.*;
import java.time.ZonedDateTime;
import java.util.UUID;

@Entity
@Table(name = "onboarding_import_job")
public class OnboardingImportJob extends TenantAuditedEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "source_media_asset_id", nullable = false)
    private Media sourceMediaAsset;
    @Column(name = "file_name", nullable = false, length = 500) private String fileName;
    @Column(name = "file_sha256", nullable = false, length = 64, updatable = false) private String fileSha256;
    @Enumerated(EnumType.STRING) @Column(name = "format", nullable = false, length = 8) private OnboardingImportFormat format;
    @Enumerated(EnumType.STRING) @Column(name = "csv_section", length = 32) private OnboardingSection csvSection;
    @Column(name = "selected_sections", length = 255) private String selectedSections;
    @Column(name = "template_version", nullable = false) private int templateVersion = 1;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 32) private OnboardingJobStatus status;
    @Column(name = "stage", nullable = false, length = 64) private String stage;
    @Column(name = "progress_percentage", nullable = false) private int progressPercentage;
    @Column(name = "upload_idempotency_key", nullable = false, updatable = false) private UUID uploadIdempotencyKey;
    @Column(name = "apply_idempotency_key") private UUID applyIdempotencyKey;
    @Column(name = "requested_by", nullable = false, updatable = false) private String requestedBy;
    @Column(name = "executed_by") private String executedBy;
    @Column(name = "send_setup_emails", nullable = false) private boolean sendSetupEmails = true;
    @Column(name = "warnings_accepted_at") private ZonedDateTime warningsAcceptedAt;
    @Column(name = "total_rows", nullable = false) private int totalRows;
    @Column(name = "valid_rows", nullable = false) private int validRows;
    @Column(name = "warning_rows", nullable = false) private int warningRows;
    @Column(name = "error_rows", nullable = false) private int errorRows;
    @Column(name = "setup_email_failures", nullable = false) private int setupEmailFailures;
    @Column(name = "started_at") private ZonedDateTime startedAt;
    @Column(name = "completed_at") private ZonedDateTime completedAt;
    @Column(name = "last_error_code", length = 128) private String lastErrorCode;

    public Media getSourceMediaAsset() { return sourceMediaAsset; } public void setSourceMediaAsset(Media v) { sourceMediaAsset = v; }
    public String getFileName() { return fileName; } public void setFileName(String v) { fileName = v; }
    public String getFileSha256() { return fileSha256; } public void setFileSha256(String v) { fileSha256 = v; }
    public OnboardingImportFormat getFormat() { return format; } public void setFormat(OnboardingImportFormat v) { format = v; }
    public OnboardingSection getCsvSection() { return csvSection; } public void setCsvSection(OnboardingSection v) { csvSection = v; }
    public String getSelectedSections() { return selectedSections; } public void setSelectedSections(String v) { selectedSections = v; }
    public int getTemplateVersion() { return templateVersion; } public void setTemplateVersion(int v) { templateVersion = v; }
    public OnboardingJobStatus getStatus() { return status; } public void setStatus(OnboardingJobStatus v) { status = v; }
    public String getStage() { return stage; } public void setStage(String v) { stage = v; }
    public int getProgressPercentage() { return progressPercentage; } public void setProgressPercentage(int v) { progressPercentage = v; }
    public UUID getUploadIdempotencyKey() { return uploadIdempotencyKey; } public void setUploadIdempotencyKey(UUID v) { uploadIdempotencyKey = v; }
    public UUID getApplyIdempotencyKey() { return applyIdempotencyKey; } public void setApplyIdempotencyKey(UUID v) { applyIdempotencyKey = v; }
    public String getRequestedBy() { return requestedBy; } public void setRequestedBy(String v) { requestedBy = v; }
    public String getExecutedBy() { return executedBy; } public void setExecutedBy(String v) { executedBy = v; }
    public boolean isSendSetupEmails() { return sendSetupEmails; } public void setSendSetupEmails(boolean v) { sendSetupEmails = v; }
    public ZonedDateTime getWarningsAcceptedAt() { return warningsAcceptedAt; } public void setWarningsAcceptedAt(ZonedDateTime v) { warningsAcceptedAt = v; }
    public int getTotalRows() { return totalRows; } public void setTotalRows(int v) { totalRows = v; }
    public int getValidRows() { return validRows; } public void setValidRows(int v) { validRows = v; }
    public int getWarningRows() { return warningRows; } public void setWarningRows(int v) { warningRows = v; }
    public int getErrorRows() { return errorRows; } public void setErrorRows(int v) { errorRows = v; }
    public int getSetupEmailFailures() { return setupEmailFailures; } public void setSetupEmailFailures(int v) { setupEmailFailures = v; }
    public ZonedDateTime getStartedAt() { return startedAt; } public void setStartedAt(ZonedDateTime v) { startedAt = v; }
    public ZonedDateTime getCompletedAt() { return completedAt; } public void setCompletedAt(ZonedDateTime v) { completedAt = v; }
    public String getLastErrorCode() { return lastErrorCode; } public void setLastErrorCode(String v) { lastErrorCode = v; }
}
