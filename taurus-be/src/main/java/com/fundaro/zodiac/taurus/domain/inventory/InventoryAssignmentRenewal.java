package com.fundaro.zodiac.taurus.domain.inventory;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "inventory_assignment_renewal")
public class InventoryAssignmentRenewal extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false)
    private InventoryAssignment assignment;

    @Column(name = "sheet_id", nullable = false)
    private Long sheetId;

    @Column(name = "renewed_on", nullable = false)
    private LocalDate renewedOn;

    @Column(name = "previous_expiration_date", nullable = false)
    private LocalDate previousExpirationDate;

    @Column(name = "new_expiration_date", nullable = false)
    private LocalDate newExpirationDate;

    @Column(name = "authorization_reference", nullable = false, length = 128)
    private String authorizationReference;

    @Column(name = "authorization_date", nullable = false)
    private LocalDate authorizationDate;

    public InventoryAssignment getAssignment() {
        return assignment;
    }

    public void setAssignment(InventoryAssignment assignment) {
        this.assignment = assignment;
    }

    public Long getSheetId() {
        return sheetId;
    }

    public void setSheetId(Long sheetId) {
        this.sheetId = sheetId;
    }

    public LocalDate getRenewedOn() {
        return renewedOn;
    }

    public void setRenewedOn(LocalDate renewedOn) {
        this.renewedOn = renewedOn;
    }

    public LocalDate getPreviousExpirationDate() {
        return previousExpirationDate;
    }

    public void setPreviousExpirationDate(LocalDate previousExpirationDate) {
        this.previousExpirationDate = previousExpirationDate;
    }

    public LocalDate getNewExpirationDate() {
        return newExpirationDate;
    }

    public void setNewExpirationDate(LocalDate newExpirationDate) {
        this.newExpirationDate = newExpirationDate;
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
}
