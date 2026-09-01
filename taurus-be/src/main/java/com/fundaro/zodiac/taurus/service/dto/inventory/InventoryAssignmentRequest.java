package com.fundaro.zodiac.taurus.service.dto.inventory;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record InventoryAssignmentRequest(
    @NotNull Long userIndex,
    @Min(0) int order,
    @Min(1) int quantity,
    String description,
    LocalDate expirationDate
) {}
