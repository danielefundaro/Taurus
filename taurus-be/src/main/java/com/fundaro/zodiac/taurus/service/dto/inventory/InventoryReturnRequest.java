package com.fundaro.zodiac.taurus.service.dto.inventory;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryCondition;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record InventoryReturnRequest(@Min(1) int quantity, InventoryCondition condition, @Size(max = 2000) String notes) {}
