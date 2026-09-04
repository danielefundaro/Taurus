package com.fundaro.zodiac.taurus.service.dto;

public record TenantFeaturesDTO(
    String tenantCode,
    Long version,
    boolean financeEnabled,
    boolean inventoryEnabled
) {}
