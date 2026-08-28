package com.fundaro.zodiac.taurus.service.dto.inventory;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record InventoryPhotoOrderRequest(@NotEmpty List<@NotNull Long> photoIds) {}
