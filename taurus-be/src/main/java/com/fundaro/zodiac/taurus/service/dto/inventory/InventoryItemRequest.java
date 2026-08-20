package com.fundaro.zodiac.taurus.service.dto.inventory;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryCondition;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record InventoryItemRequest(
    @NotBlank @Size(max = 128) String inventoryNumber,
    @NotBlank @Size(max = 255) String name,
    @Size(max = 4000) String description,
    @Min(0) int totalQuantity,
    @DecimalMin("0.0") BigDecimal estimatedUnitValue,
    @Pattern(regexp = "[A-Za-z]{3}") String currency,
    @NotNull InventoryCondition conditionStatus,
    @Size(max = 2000) String conditionNotes
) {}
