package com.fundaro.zodiac.taurus.service.dto;

import com.fundaro.zodiac.taurus.domain.enumeration.TenantFeature;

import java.util.List;

public record TenantFeatureCapabilityDTO(boolean available, String reasonCode, List<TenantFeature> dependencies) {
}
