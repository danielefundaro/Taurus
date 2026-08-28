package com.fundaro.zodiac.taurus.service.dto.inventory;

public record InventoryAdminSummaryDTO(
    long registeredItems,
    long totalQuantity,
    long assignedQuantity,
    long availableQuantity,
    long pendingDecisions,
    long pendingReturns
) {}
