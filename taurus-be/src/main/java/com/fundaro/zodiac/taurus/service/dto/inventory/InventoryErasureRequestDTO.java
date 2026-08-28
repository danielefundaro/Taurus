package com.fundaro.zodiac.taurus.service.dto.inventory;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryErasureStatus;
import java.time.ZonedDateTime;

public record InventoryErasureRequestDTO(Long id, Long userIndex, String displayName, String email, InventoryErasureStatus status, ZonedDateTime requestedAt) {}
