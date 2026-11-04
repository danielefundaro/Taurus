package com.fundaro.zodiac.taurus.service.dto.guidedtour;

import jakarta.validation.constraints.NotNull;

/**
 * {@code version} is the one read with the settings, {@code null} when the profile was never written.
 */
public record GuidedTourSettingsRequest(@NotNull Boolean autoStartEnabled, Long version) {
}
