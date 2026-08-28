package com.fundaro.zodiac.taurus.service.dto.inventory;

import java.time.ZonedDateTime;

public record InventoryUserSummaryDTO(
    long possessedItems,
    long outstandingQuantity,
    long pendingDecisions,
    ZonedDateTime lastAssignedAt
) {}
