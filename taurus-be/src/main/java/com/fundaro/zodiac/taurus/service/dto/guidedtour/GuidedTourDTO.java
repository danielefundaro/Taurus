package com.fundaro.zodiac.taurus.service.dto.guidedtour;

import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;

/**
 * The guided tour of the session: the settings of the person in the tenant and the progress of the role of the
 * token. {@code progress} is {@code null} when the role has never started the tour; {@code enabled} reports the
 * application switch.
 */
public record GuidedTourDTO(boolean enabled, GuidedTourSettingsDTO settings, RoleEnum role, GuidedTourProgressDTO progress) {
}
