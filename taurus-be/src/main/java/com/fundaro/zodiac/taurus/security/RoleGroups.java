package com.fundaro.zodiac.taurus.security;

import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

/**
 * Named groups of the roles that share the same permissions. A check lists a group instead of single roles, so a new
 * role is added in one place. The authority arrays are copies: callers may not change the groups.
 */
public final class RoleGroups {

    /** Platform and tenant administrators: tenant configuration, technical tools and the administrators themselves. */
    private static final String[] TENANT_ADMINS = { AuthoritiesConstants.SUPER_ADMIN, AuthoritiesConstants.ADMIN };

    /** Who manages the association: catalogue, finance, inventory, calendar, events and users. */
    private static final String[] TENANT_MANAGERS = { AuthoritiesConstants.SUPER_ADMIN, AuthoritiesConstants.ADMIN, AuthoritiesConstants.BOARD };

    public static final Set<RoleEnum> TENANT_ADMIN_ROLES = Set.of(RoleEnum.ROLE_SUPER_ADMIN, RoleEnum.ROLE_ADMIN);

    public static final Set<RoleEnum> TENANT_MANAGER_ROLES = Set.of(RoleEnum.ROLE_SUPER_ADMIN, RoleEnum.ROLE_ADMIN, RoleEnum.ROLE_BOARD);

    public static final Set<RoleEnum> CATALOGUE_MANAGER_ROLES = Set.of(
        RoleEnum.ROLE_SUPER_ADMIN,
        RoleEnum.ROLE_ADMIN,
        RoleEnum.ROLE_BOARD,
        RoleEnum.ROLE_ARCHIVIST
    );

    public static final Set<RoleEnum> FINANCE_MANAGER_ROLES = Set.of(
        RoleEnum.ROLE_SUPER_ADMIN,
        RoleEnum.ROLE_ADMIN,
        RoleEnum.ROLE_BOARD,
        RoleEnum.ROLE_TREASURER
    );

    /** Members who can sign for the association: the platform super administrator is not one of them. */
    public static final List<RoleEnum> ASSOCIATION_SIGNER_ROLES = List.of(RoleEnum.ROLE_ADMIN, RoleEnum.ROLE_BOARD);

    private RoleGroups() {}

    public static String[] tenantAdmins() {
        return TENANT_ADMINS.clone();
    }

    public static String[] tenantManagers() {
        return TENANT_MANAGERS.clone();
    }

    /** The tenant managers together with the given roles, for example the archivist of the catalogue. */
    public static String[] tenantManagersAnd(String... authorities) {
        return Stream.concat(Arrays.stream(TENANT_MANAGERS), Arrays.stream(authorities)).distinct().toArray(String[]::new);
    }

    public static String[] catalogueManagers() {
        return tenantManagersAnd(AuthoritiesConstants.ARCHIVIST);
    }

    public static String[] financeManagers() {
        return tenantManagersAnd(AuthoritiesConstants.TREASURER);
    }

    public static boolean isTenantAdmin(Authentication authentication) {
        return hasAny(authentication, TENANT_ADMINS);
    }

    public static boolean isTenantManager(Authentication authentication) {
        return hasAny(authentication, TENANT_MANAGERS);
    }

    public static boolean isCatalogueManager(Authentication authentication) {
        return hasAny(authentication, catalogueManagers());
    }

    public static boolean hasAny(Authentication authentication, String... authorities) {
        if (authentication == null || authentication.getAuthorities() == null) return false;
        Set<String> wanted = Set.of(authorities);
        return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).anyMatch(wanted::contains);
    }
}
