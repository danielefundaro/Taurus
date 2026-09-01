package com.fundaro.zodiac.taurus.service.dto.inventory;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryAssignmentStatus;
import java.time.ZonedDateTime;
import java.time.LocalDate;
import java.math.BigDecimal;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryCondition;
import java.util.List;

public record InventoryAssignmentDTO(
    Long id,
    Long itemId,
    String inventoryNumber,
    String itemName,
    String itemDescription,
    BigDecimal estimatedUnitValue,
    String currency,
    InventoryCondition conditionStatus,
    String conditionNotes,
    LocalDate expirationDate,
    Long userIndex,
    String userName,
    String userLastName,
    int order,
    int assignedQuantity,
    int returnedQuantity,
    int outstandingQuantity,
    ZonedDateTime assignedAt,
    String description,
    InventoryAssignmentStatus status,
    int revision,
    String revisionHash,
    ZonedDateTime revisionDate,
    InventoryDecisionDTO decision,
    List<InventoryReturnDTO> returns,
    List<InventoryPhotoDTO> photos
) {}
