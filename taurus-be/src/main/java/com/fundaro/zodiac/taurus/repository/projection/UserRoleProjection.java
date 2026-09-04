package com.fundaro.zodiac.taurus.repository.projection;

import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;

public interface UserRoleProjection {
    String getKeycloakId();

    RoleEnum getRole();
}
