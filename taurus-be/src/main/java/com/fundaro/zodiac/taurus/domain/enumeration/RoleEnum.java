package com.fundaro.zodiac.taurus.domain.enumeration;

/**
 * The RoleEnum enumeration.
 */
public enum RoleEnum {
    ROLE_SUPER_ADMIN("Platform super administrator"),
    ROLE_ADMIN("Tenant administrator"),
    ROLE_BOARD("Tenant board member"),
    ROLE_TREASURER("Tenant finance manager"),
    ROLE_ARCHIVIST("Tenant catalogue manager"),
    ROLE_USER("Tenant member"),
    ROLE_USER_EXTERNAL("Tenant external member");

    private final String keycloakDescription;

    RoleEnum(String keycloakDescription) {
        this.keycloakDescription = keycloakDescription;
    }

    /**
     * Description of the Keycloak client role, set when the role is provisioned.
     */
    public String getKeycloakDescription() {
        return keycloakDescription;
    }

    @Override
    public String toString() {
        return mapToString(this);
    }

    public static String mapToString(RoleEnum roleEnum) {
        String role = "";

        switch (roleEnum) {
            case ROLE_SUPER_ADMIN -> role = "ROLE_SUPER_ADMIN";
            case ROLE_ADMIN -> role = "ROLE_ADMIN";
            case ROLE_BOARD -> role = "ROLE_BOARD";
            case ROLE_TREASURER -> role = "ROLE_TREASURER";
            case ROLE_ARCHIVIST -> role = "ROLE_ARCHIVIST";
            case ROLE_USER -> role = "ROLE_USER";
            case ROLE_USER_EXTERNAL -> role = "ROLE_USER_EXTERNAL";
        }

        return role;
    }
}
