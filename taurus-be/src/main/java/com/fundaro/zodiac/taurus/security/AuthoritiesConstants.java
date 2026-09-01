package com.fundaro.zodiac.taurus.security;

/**
 * Constants for Spring Security authorities.
 */
public final class AuthoritiesConstants {

    public static final String SUPER_ADMIN = "ROLE_SUPER_ADMIN";

    public static final String ADMIN = "ROLE_ADMIN";

    public static final String TREASURER = "ROLE_TREASURER";

    public static final String ARCHIVIST = "ROLE_ARCHIVIST";

    public static final String USER = "ROLE_USER";

    public static final String USER_EXTERNAL = "ROLE_USER_EXTERNAL";

    public static final String ANONYMOUS = "ROLE_ANONYMOUS";

    private AuthoritiesConstants() {}
}
