package com.fundaro.zodiac.taurus.service.dto.inventory;

import com.fundaro.zodiac.taurus.domain.inventory.InventoryCondition;
import com.fundaro.zodiac.taurus.domain.inventory.InventoryReturnStatus;
import java.time.ZonedDateTime;

public record InventoryReturnDTO(Long id, int quantity, InventoryReturnStatus status, ZonedDateTime requestedAt, ZonedDateTime completedAt, InventoryCondition condition, String notes, java.util.List<InventoryPhotoDTO> photos) {}
