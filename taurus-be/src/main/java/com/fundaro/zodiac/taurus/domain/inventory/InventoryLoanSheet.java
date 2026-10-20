package com.fundaro.zodiac.taurus.domain.inventory;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZonedDateTime;

@Entity
@Table(name = "inventory_loan_sheet")
public class InventoryLoanSheet extends TenantAuditedEntity {

    @Column(name = "sheet_year", nullable = false)
    private int sheetYear;

    @Column(name = "sheet_sequence", nullable = false)
    private int sheetSequence;

    @Column(name = "sheet_number", nullable = false, length = 16)
    private String sheetNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private InventoryLoanSheetStatus status;

    @Column(name = "user_index", nullable = false)
    private Long userIndex;

    @Column(name = "user_keycloak_id", nullable = false)
    private String userKeycloakId;

    @Column(name = "member_name", nullable = false)
    private String memberName;

    @Column(name = "member_last_name", nullable = false)
    private String memberLastName;

    @Column(name = "member_birth_date")
    private LocalDate memberBirthDate;

    @Column(name = "member_email")
    private String memberEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "holder_role", nullable = false, length = 16)
    private InventoryLoanHolderRole holderRole;

    @Column(name = "issued_on", nullable = false)
    private LocalDate issuedOn;

    @Column(name = "place", length = 255)
    private String place;

    @Column(name = "authorization_reference", length = 128)
    private String authorizationReference;

    @Column(name = "authorization_date")
    private LocalDate authorizationDate;

    @Column(name = "association_signatory_name", length = 255)
    private String associationSignatoryName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "template_id", nullable = false)
    private InventoryLoanTemplate template;

    @Column(name = "template_hash", nullable = false, length = 64)
    private String templateHash;

    @Column(name = "min_months", nullable = false)
    private int minMonths;

    @Column(name = "max_months", nullable = false)
    private int maxMonths;

    @Column(name = "renewal_max_months", nullable = false)
    private int renewalMaxMonths;

    @Column(name = "total_max_months", nullable = false)
    private int totalMaxMonths;

    @Column(name = "closed_at")
    private ZonedDateTime closedAt;

    @Column(name = "closed_by")
    private String closedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "return_outcome", length = 16)
    private InventoryLoanReturnOutcome returnOutcome;

    @Column(name = "charge_amount", precision = 19, scale = 2)
    private BigDecimal chargeAmount;

    @Column(name = "charge_due_date")
    private LocalDate chargeDueDate;

    @Column(name = "received_by_name", length = 255)
    private String receivedByName;

    @Column(name = "settled_at")
    private ZonedDateTime settledAt;

    @Column(name = "settled_by")
    private String settledBy;

    @Column(name = "personal_data_purged_at")
    private ZonedDateTime personalDataPurgedAt;

    @Column(name = "cancelled_at")
    private ZonedDateTime cancelledAt;

    @Column(name = "cancelled_by")
    private String cancelledBy;

    @Column(name = "cancellation_reason", length = 1000)
    private String cancellationReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "signature_mode", nullable = false, length = 16)
    private InventoryLoanSignatureMode signatureMode = InventoryLoanSignatureMode.PAPER;

    @Column(name = "holder_email")
    private String holderEmail;

    @Column(name = "holder_name")
    private String holderName;

    @Column(name = "holder_tax_code", length = 16)
    private String holderTaxCode;

    @Column(name = "holder_no_tax_code", nullable = false)
    private boolean holderNoTaxCode;

    public int getSheetYear() {
        return sheetYear;
    }

    public void setSheetYear(int sheetYear) {
        this.sheetYear = sheetYear;
    }

    public int getSheetSequence() {
        return sheetSequence;
    }

    public void setSheetSequence(int sheetSequence) {
        this.sheetSequence = sheetSequence;
    }

    public String getSheetNumber() {
        return sheetNumber;
    }

    public void setSheetNumber(String sheetNumber) {
        this.sheetNumber = sheetNumber;
    }

    public InventoryLoanSheetStatus getStatus() {
        return status;
    }

    public void setStatus(InventoryLoanSheetStatus status) {
        this.status = status;
    }

    public Long getUserIndex() {
        return userIndex;
    }

    public void setUserIndex(Long userIndex) {
        this.userIndex = userIndex;
    }

    public String getUserKeycloakId() {
        return userKeycloakId;
    }

    public void setUserKeycloakId(String userKeycloakId) {
        this.userKeycloakId = userKeycloakId;
    }

    public String getMemberName() {
        return memberName;
    }

    public void setMemberName(String memberName) {
        this.memberName = memberName;
    }

    public String getMemberLastName() {
        return memberLastName;
    }

    public void setMemberLastName(String memberLastName) {
        this.memberLastName = memberLastName;
    }

    public LocalDate getMemberBirthDate() {
        return memberBirthDate;
    }

    public void setMemberBirthDate(LocalDate memberBirthDate) {
        this.memberBirthDate = memberBirthDate;
    }

    public String getMemberEmail() {
        return memberEmail;
    }

    public void setMemberEmail(String memberEmail) {
        this.memberEmail = memberEmail;
    }

    public InventoryLoanHolderRole getHolderRole() {
        return holderRole;
    }

    public void setHolderRole(InventoryLoanHolderRole holderRole) {
        this.holderRole = holderRole;
    }

    public LocalDate getIssuedOn() {
        return issuedOn;
    }

    public void setIssuedOn(LocalDate issuedOn) {
        this.issuedOn = issuedOn;
    }

    public String getPlace() {
        return place;
    }

    public void setPlace(String place) {
        this.place = place;
    }

    public String getAuthorizationReference() {
        return authorizationReference;
    }

    public void setAuthorizationReference(String authorizationReference) {
        this.authorizationReference = authorizationReference;
    }

    public LocalDate getAuthorizationDate() {
        return authorizationDate;
    }

    public void setAuthorizationDate(LocalDate authorizationDate) {
        this.authorizationDate = authorizationDate;
    }

    public String getAssociationSignatoryName() {
        return associationSignatoryName;
    }

    public void setAssociationSignatoryName(String associationSignatoryName) {
        this.associationSignatoryName = associationSignatoryName;
    }

    public InventoryLoanTemplate getTemplate() {
        return template;
    }

    public void setTemplate(InventoryLoanTemplate template) {
        this.template = template;
    }

    public String getTemplateHash() {
        return templateHash;
    }

    public void setTemplateHash(String templateHash) {
        this.templateHash = templateHash;
    }

    public int getMinMonths() {
        return minMonths;
    }

    public void setMinMonths(int minMonths) {
        this.minMonths = minMonths;
    }

    public int getMaxMonths() {
        return maxMonths;
    }

    public void setMaxMonths(int maxMonths) {
        this.maxMonths = maxMonths;
    }

    public int getRenewalMaxMonths() {
        return renewalMaxMonths;
    }

    public void setRenewalMaxMonths(int renewalMaxMonths) {
        this.renewalMaxMonths = renewalMaxMonths;
    }

    public int getTotalMaxMonths() {
        return totalMaxMonths;
    }

    public void setTotalMaxMonths(int totalMaxMonths) {
        this.totalMaxMonths = totalMaxMonths;
    }

    public ZonedDateTime getClosedAt() {
        return closedAt;
    }

    public void setClosedAt(ZonedDateTime closedAt) {
        this.closedAt = closedAt;
    }

    public String getClosedBy() {
        return closedBy;
    }

    public void setClosedBy(String closedBy) {
        this.closedBy = closedBy;
    }

    public InventoryLoanReturnOutcome getReturnOutcome() {
        return returnOutcome;
    }

    public void setReturnOutcome(InventoryLoanReturnOutcome returnOutcome) {
        this.returnOutcome = returnOutcome;
    }

    public BigDecimal getChargeAmount() {
        return chargeAmount;
    }

    public void setChargeAmount(BigDecimal chargeAmount) {
        this.chargeAmount = chargeAmount;
    }

    public LocalDate getChargeDueDate() {
        return chargeDueDate;
    }

    public void setChargeDueDate(LocalDate chargeDueDate) {
        this.chargeDueDate = chargeDueDate;
    }

    public String getReceivedByName() {
        return receivedByName;
    }

    public void setReceivedByName(String receivedByName) {
        this.receivedByName = receivedByName;
    }

    public ZonedDateTime getSettledAt() {
        return settledAt;
    }

    public void setSettledAt(ZonedDateTime settledAt) {
        this.settledAt = settledAt;
    }

    public String getSettledBy() {
        return settledBy;
    }

    public void setSettledBy(String settledBy) {
        this.settledBy = settledBy;
    }

    public ZonedDateTime getPersonalDataPurgedAt() {
        return personalDataPurgedAt;
    }

    public void setPersonalDataPurgedAt(ZonedDateTime personalDataPurgedAt) {
        this.personalDataPurgedAt = personalDataPurgedAt;
    }

    public ZonedDateTime getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(ZonedDateTime cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public String getCancelledBy() {
        return cancelledBy;
    }

    public void setCancelledBy(String cancelledBy) {
        this.cancelledBy = cancelledBy;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public void setCancellationReason(String cancellationReason) {
        this.cancellationReason = cancellationReason;
    }

    public InventoryLoanSignatureMode getSignatureMode() {
        return signatureMode;
    }

    public void setSignatureMode(InventoryLoanSignatureMode signatureMode) {
        this.signatureMode = signatureMode;
    }

    public String getHolderEmail() {
        return holderEmail;
    }

    public void setHolderEmail(String holderEmail) {
        this.holderEmail = holderEmail;
    }

    public String getHolderName() {
        return holderName;
    }

    public void setHolderName(String holderName) {
        this.holderName = holderName;
    }

    public String getHolderTaxCode() {
        return holderTaxCode;
    }

    public void setHolderTaxCode(String holderTaxCode) {
        this.holderTaxCode = holderTaxCode;
    }

    public boolean isHolderNoTaxCode() {
        return holderNoTaxCode;
    }

    public void setHolderNoTaxCode(boolean holderNoTaxCode) {
        this.holderNoTaxCode = holderNoTaxCode;
    }
}
