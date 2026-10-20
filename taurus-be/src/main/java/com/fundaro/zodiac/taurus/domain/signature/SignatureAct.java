package com.fundaro.zodiac.taurus.domain.signature;

import com.fundaro.zodiac.taurus.domain.inventory.TenantAuditedEntity;
import jakarta.persistence.*;

import java.time.ZonedDateTime;

/**
 * An operation to sign, with the entity it comes from: a loan sheet and its variation or return, or an assignment
 * decision.
 */
@Entity
@Table(name = "signature_act")
public class SignatureAct extends TenantAuditedEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "act_type", nullable = false, length = 32)
    private SignatureActType actType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private SignatureActStatus status;

    @Column(name = "sheet_id")
    private Long sheetId;

    @Column(name = "assignment_id")
    private Long assignmentId;

    @Column(name = "renewal_id")
    private Long renewalId;

    @Column(name = "return_id")
    private Long returnId;

    @Column(name = "decision_id")
    private Long decisionId;

    @Column(name = "template_id")
    private Long templateId;

    @Column(name = "template_hash", length = 64)
    private String templateHash;

    @Column(name = "attestation_media_asset_id")
    private Long attestationMediaAssetId;

    @Column(name = "completed_at")
    private ZonedDateTime completedAt;

    @Column(name = "paper_note", length = 2000)
    private String paperNote;

    @Column(name = "purge_after")
    private ZonedDateTime purgeAfter;

    @Column(name = "purged_at")
    private ZonedDateTime purgedAt;

    @Column(name = "cancelled_at")
    private ZonedDateTime cancelledAt;

    public SignatureActType getActType() {
        return actType;
    }

    public void setActType(SignatureActType actType) {
        this.actType = actType;
    }

    public SignatureActStatus getStatus() {
        return status;
    }

    public void setStatus(SignatureActStatus status) {
        this.status = status;
    }

    public Long getSheetId() {
        return sheetId;
    }

    public void setSheetId(Long sheetId) {
        this.sheetId = sheetId;
    }

    public Long getAssignmentId() {
        return assignmentId;
    }

    public void setAssignmentId(Long assignmentId) {
        this.assignmentId = assignmentId;
    }

    public Long getRenewalId() {
        return renewalId;
    }

    public void setRenewalId(Long renewalId) {
        this.renewalId = renewalId;
    }

    public Long getReturnId() {
        return returnId;
    }

    public void setReturnId(Long returnId) {
        this.returnId = returnId;
    }

    public Long getDecisionId() {
        return decisionId;
    }

    public void setDecisionId(Long decisionId) {
        this.decisionId = decisionId;
    }

    public Long getTemplateId() {
        return templateId;
    }

    public void setTemplateId(Long templateId) {
        this.templateId = templateId;
    }

    public String getTemplateHash() {
        return templateHash;
    }

    public void setTemplateHash(String templateHash) {
        this.templateHash = templateHash;
    }

    public Long getAttestationMediaAssetId() {
        return attestationMediaAssetId;
    }

    public void setAttestationMediaAssetId(Long attestationMediaAssetId) {
        this.attestationMediaAssetId = attestationMediaAssetId;
    }

    public ZonedDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(ZonedDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public String getPaperNote() {
        return paperNote;
    }

    public void setPaperNote(String paperNote) {
        this.paperNote = paperNote;
    }

    public ZonedDateTime getPurgeAfter() {
        return purgeAfter;
    }

    public void setPurgeAfter(ZonedDateTime purgeAfter) {
        this.purgeAfter = purgeAfter;
    }

    public ZonedDateTime getPurgedAt() {
        return purgedAt;
    }

    public void setPurgedAt(ZonedDateTime purgedAt) {
        this.purgedAt = purgedAt;
    }

    public ZonedDateTime getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(ZonedDateTime cancelledAt) {
        this.cancelledAt = cancelledAt;
    }
}
