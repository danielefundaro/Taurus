package com.fundaro.zodiac.taurus.domain.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;

@Entity
@Table(name = "inventory_item")
public class InventoryItem extends TenantAuditedEntity {

    @Column(name = "inventory_number", nullable = false, length = 128)
    private String inventoryNumber;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "total_quantity", nullable = false)
    private int totalQuantity;

    @Column(name = "estimated_unit_value", precision = 19, scale = 4)
    private BigDecimal estimatedUnitValue;

    @Column(name = "currency", length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_status", nullable = false, length = 32)
    private InventoryCondition conditionStatus;

    @Column(name = "condition_notes", length = 2000)
    private String conditionNotes;

    public String getInventoryNumber() { return inventoryNumber; }
    public void setInventoryNumber(String inventoryNumber) { this.inventoryNumber = inventoryNumber; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public int getTotalQuantity() { return totalQuantity; }
    public void setTotalQuantity(int totalQuantity) { this.totalQuantity = totalQuantity; }
    public BigDecimal getEstimatedUnitValue() { return estimatedUnitValue; }
    public void setEstimatedUnitValue(BigDecimal estimatedUnitValue) { this.estimatedUnitValue = estimatedUnitValue; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public InventoryCondition getConditionStatus() { return conditionStatus; }
    public void setConditionStatus(InventoryCondition conditionStatus) { this.conditionStatus = conditionStatus; }
    public String getConditionNotes() { return conditionNotes; }
    public void setConditionNotes(String conditionNotes) { this.conditionNotes = conditionNotes; }
}
