package com.fundaro.zodiac.taurus.domain.inventory;

import jakarta.persistence.*;

/**
 * Loan sheet settings of the tenant, stored in a single row: template in use and duration rules for new sheets.
 */
@Entity
@Table(name = "inventory_loan_settings")
public class InventoryLoanSettings extends TenantAuditedEntity {

    /**
     * Template uploaded by the tenant and in use; {@code null} when new sheets use the Taurus default.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "active_template_id")
    private InventoryLoanTemplate activeTemplate;

    @Column(name = "min_months", nullable = false)
    private int minMonths;

    @Column(name = "max_months", nullable = false)
    private int maxMonths;

    @Column(name = "renewal_max_months", nullable = false)
    private int renewalMaxMonths;

    @Column(name = "total_max_months", nullable = false)
    private int totalMaxMonths;

    public InventoryLoanTemplate getActiveTemplate() {
        return activeTemplate;
    }

    public void setActiveTemplate(InventoryLoanTemplate activeTemplate) {
        this.activeTemplate = activeTemplate;
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
}
