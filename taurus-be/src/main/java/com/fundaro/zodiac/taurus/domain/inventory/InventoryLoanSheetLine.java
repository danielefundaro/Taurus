package com.fundaro.zodiac.taurus.domain.inventory;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(
    name = "inventory_loan_sheet_line",
    uniqueConstraints = @UniqueConstraint(name = "uq_inventory_loan_sheet_line", columnNames = {"sheet_id", "line_number"})
)
public class InventoryLoanSheetLine extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sheet_id", nullable = false)
    private InventoryLoanSheet sheet;

    @Column(name = "line_number", nullable = false)
    private int lineNumber;

    @Column(name = "assignment_id", nullable = false)
    private Long assignmentId;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Column(name = "brand_model")
    private String brandModel;

    @Column(name = "serial_number", length = 128)
    private String serialNumber;

    @Column(name = "inventory_number", nullable = false, length = 128)
    private String inventoryNumber;

    @Column(name = "accessories", length = 500)
    private String accessories;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "agreed_unit_value", precision = 19, scale = 4)
    private BigDecimal agreedUnitValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_status", length = 32)
    private InventoryCondition conditionStatus;

    @Column(name = "condition_notes", length = 2000)
    private String conditionNotes;

    @Enumerated(EnumType.STRING)
    @Column(name = "loan_type", nullable = false, length = 32)
    private InventoryLoanType loanType;

    @Column(name = "expiration_date")
    private LocalDate expirationDate;

    public InventoryLoanSheet getSheet() {
        return sheet;
    }

    public void setSheet(InventoryLoanSheet sheet) {
        this.sheet = sheet;
    }

    public int getLineNumber() {
        return lineNumber;
    }

    public void setLineNumber(int lineNumber) {
        this.lineNumber = lineNumber;
    }

    public Long getAssignmentId() {
        return assignmentId;
    }

    public void setAssignmentId(Long assignmentId) {
        this.assignmentId = assignmentId;
    }

    public String getItemName() {
        return itemName;
    }

    public void setItemName(String itemName) {
        this.itemName = itemName;
    }

    public String getBrandModel() {
        return brandModel;
    }

    public void setBrandModel(String brandModel) {
        this.brandModel = brandModel;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getInventoryNumber() {
        return inventoryNumber;
    }

    public void setInventoryNumber(String inventoryNumber) {
        this.inventoryNumber = inventoryNumber;
    }

    public String getAccessories() {
        return accessories;
    }

    public void setAccessories(String accessories) {
        this.accessories = accessories;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public BigDecimal getAgreedUnitValue() {
        return agreedUnitValue;
    }

    public void setAgreedUnitValue(BigDecimal agreedUnitValue) {
        this.agreedUnitValue = agreedUnitValue;
    }

    public InventoryCondition getConditionStatus() {
        return conditionStatus;
    }

    public void setConditionStatus(InventoryCondition conditionStatus) {
        this.conditionStatus = conditionStatus;
    }

    public String getConditionNotes() {
        return conditionNotes;
    }

    public void setConditionNotes(String conditionNotes) {
        this.conditionNotes = conditionNotes;
    }

    public InventoryLoanType getLoanType() {
        return loanType;
    }

    public void setLoanType(InventoryLoanType loanType) {
        this.loanType = loanType;
    }

    public LocalDate getExpirationDate() {
        return expirationDate;
    }

    public void setExpirationDate(LocalDate expirationDate) {
        this.expirationDate = expirationDate;
    }
}
