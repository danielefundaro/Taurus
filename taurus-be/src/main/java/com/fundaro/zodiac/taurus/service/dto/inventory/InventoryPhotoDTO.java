package com.fundaro.zodiac.taurus.service.dto.inventory;

public record InventoryPhotoDTO(Long id, String fileName, String contentType, long fileSize, int displayOrder, boolean preview) {}
