package com.fundaro.zodiac.taurus.domain.inventory;

import jakarta.persistence.*;

@Entity
@Table(name = "inventory_loan_sheet_counter")
public class InventoryLoanSheetCounter extends AuditedEntity {

    @Column(name = "sheet_year", nullable = false, unique = true)
    private int sheetYear;

    @Column(name = "last_sequence", nullable = false)
    private int lastSequence;

    public int getSheetYear() {
        return sheetYear;
    }

    public void setSheetYear(int sheetYear) {
        this.sheetYear = sheetYear;
    }

    public int getLastSequence() {
        return lastSequence;
    }

    public void setLastSequence(int lastSequence) {
        this.lastSequence = lastSequence;
    }
}
