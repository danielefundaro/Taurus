package com.fundaro.zodiac.taurus.service.dto.inventory;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryDecisionType;
import java.time.ZonedDateTime;

public record InventoryDecisionDTO(InventoryDecisionType decision, String rejectionReason, ZonedDateTime decidedAt) {}
