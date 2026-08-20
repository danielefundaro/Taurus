package com.fundaro.zodiac.taurus.service.dto.inventory;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryDecisionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record InventoryDecisionRequest(
    @NotNull InventoryDecisionType decision,
    @Size(max = 2000) String rejectionReason,
    @NotBlank @Size(min = 64, max = 64) String revisionHash
) {}
