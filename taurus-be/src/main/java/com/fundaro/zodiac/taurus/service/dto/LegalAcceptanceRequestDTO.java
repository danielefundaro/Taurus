package com.fundaro.zodiac.taurus.service.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.Set;

public record LegalAcceptanceRequestDTO(@NotEmpty Set<Long> documentIds) {
}
