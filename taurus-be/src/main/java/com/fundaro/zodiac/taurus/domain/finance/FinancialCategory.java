package com.fundaro.zodiac.taurus.domain.finance;

import com.fundaro.zodiac.taurus.domain.inventory.TenantAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

@Entity
@Table(name = "financial_category")
public class FinancialCategory extends TenantAuditedEntity {

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 16)
    private FinancialCategoryDirection direction;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "system_defined", nullable = false)
    private boolean systemDefined;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public FinancialCategoryDirection getDirection() { return direction; }
    public void setDirection(FinancialCategoryDirection direction) { this.direction = direction; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public boolean isSystemDefined() { return systemDefined; }
    public void setSystemDefined(boolean systemDefined) { this.systemDefined = systemDefined; }
    public int getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(int displayOrder) { this.displayOrder = displayOrder; }
}
