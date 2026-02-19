package com.fundaro.zodiac.taurus.domain.enumeration;

/**
 * The RoleEnum enumeration.
 */
public enum RoleEnum {
    ROLE_SUPER_ADMIN,
    ROLE_ADMIN,
    ROLE_ARCHIVIST,
    ROLE_USER;

    @Override
    public String toString() {
        return mapToString(this);
    }

    public static String mapToString(RoleEnum roleEnum) {
        String role = "";

        switch (roleEnum) {
            case ROLE_SUPER_ADMIN -> role = "ROLE_SUPER_ADMIN";
            case ROLE_ADMIN -> role = "ROLE_ADMIN";
            case ROLE_ARCHIVIST -> role = "ROLE_ARCHIVIST";
            case ROLE_USER -> role = "ROLE_USER";
        }

        return role;
    }
}
