package com.fundaro.zodiac.taurus.service.dto.inventory;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record InventoryAssignmentRequest(
    @NotBlank String userIndex,
    @Min(0) int order,
    @Min(1) int quantity,
    @Size(max = 2000) String description
) {}
