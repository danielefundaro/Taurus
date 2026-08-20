package com.fundaro.zodiac.taurus.service.dto.inventory;

import java.time.ZonedDateTime;

public record InventoryPhotoDTO(Long id, String fileName, String contentType, long fileSize, int displayOrder, ZonedDateTime insertDate) {}
