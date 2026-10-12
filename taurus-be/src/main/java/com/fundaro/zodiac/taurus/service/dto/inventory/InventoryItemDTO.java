package com.fundaro.zodiac.taurus.service.dto.inventory;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryCondition;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryLoanType;

import java.math.BigDecimal;
import java.util.List;

public record InventoryItemDTO(
    Long id,
    String inventoryNumber,
    String name,
    String description,
    int totalQuantity,
    int assignedQuantity,
    int availableQuantity,
    BigDecimal estimatedUnitValue,
    String currency,
    InventoryCondition conditionStatus,
    String conditionNotes,
    long version,
    List<InventoryPhotoDTO> photos,
    List<InventoryAssignmentDTO> assignments,
    String brandModel,
    String serialNumber,
    InventoryLoanType loanType
) {
}
