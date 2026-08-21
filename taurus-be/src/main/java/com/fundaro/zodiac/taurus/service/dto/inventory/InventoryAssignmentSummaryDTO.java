package com.fundaro.zodiac.taurus.service.dto.inventory;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignmentStatus;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryCondition;
import java.math.BigDecimal;
import java.time.ZonedDateTime;

public record InventoryAssignmentSummaryDTO(
    Long id,
    Long itemId,
    String inventoryNumber,
    String itemName,
    String itemDescription,
    BigDecimal estimatedUnitValue,
    String currency,
    InventoryCondition conditionStatus,
    int assignedQuantity,
    int returnedQuantity,
    int outstandingQuantity,
    ZonedDateTime assignedAt,
    InventoryAssignmentStatus status,
    int revision,
    ZonedDateTime revisionDate,
    InventoryDecisionDTO decision,
    InventoryPhotoDTO photo
) {}
