package com.fundaro.zodiac.taurus.service.dto.guidedtour;

/**
 * {@code version} is {@code null} until the profile is written for the first time.
 */
public record GuidedTourSettingsDTO(boolean autoStartEnabled, boolean legacyInvitation, Long version) {
}
