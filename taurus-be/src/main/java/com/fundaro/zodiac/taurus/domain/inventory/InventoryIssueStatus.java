package com.fundaro.zodiac.taurus.domain.inventory;

public enum InventoryIssueStatus {
    OPEN,
    ACKNOWLEDGED,
    RESOLVED,
    DISMISSED;

    public boolean isTerminal() {
        return this == RESOLVED || this == DISMISSED;
    }
}
