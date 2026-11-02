package com.fundaro.zodiac.taurus.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;

class RoleGroupsTest {

    @Test
    void keepsTheAdministrationToTheAdministrators() {
        assertThat(RoleGroups.tenantAdmins()).containsExactlyInAnyOrder(AuthoritiesConstants.SUPER_ADMIN, AuthoritiesConstants.ADMIN);
        assertThat(names(RoleGroups.TENANT_ADMIN_ROLES)).containsExactlyInAnyOrder(RoleGroups.tenantAdmins());
    }

    @Test
    void letsTheBoardManageTheAssociation() {
        assertThat(RoleGroups.tenantManagers()).containsExactlyInAnyOrder(
            AuthoritiesConstants.SUPER_ADMIN,
            AuthoritiesConstants.ADMIN,
            AuthoritiesConstants.BOARD
        );
        assertThat(names(RoleGroups.TENANT_MANAGER_ROLES)).containsExactlyInAnyOrder(RoleGroups.tenantManagers());
        assertThat(names(RoleGroups.CATALOGUE_MANAGER_ROLES)).containsExactlyInAnyOrder(RoleGroups.catalogueManagers());
        assertThat(names(RoleGroups.FINANCE_MANAGER_ROLES)).containsExactlyInAnyOrder(RoleGroups.financeManagers());
        assertThat(RoleGroups.catalogueManagers()).contains(AuthoritiesConstants.BOARD, AuthoritiesConstants.ARCHIVIST);
        assertThat(RoleGroups.financeManagers()).contains(AuthoritiesConstants.BOARD, AuthoritiesConstants.TREASURER);
    }

    @Test
    void signsForTheAssociationWithoutTheSuperAdministrator() {
        assertThat(RoleGroups.ASSOCIATION_SIGNER_ROLES).containsExactly(RoleEnum.ROLE_ADMIN, RoleEnum.ROLE_BOARD);
    }

    @Test
    void addsTheOtherRolesOnceAndDoesNotShareTheArrays() {
        assertThat(RoleGroups.tenantManagersAnd(AuthoritiesConstants.ARCHIVIST, AuthoritiesConstants.ADMIN)).containsExactly(
            AuthoritiesConstants.SUPER_ADMIN,
            AuthoritiesConstants.ADMIN,
            AuthoritiesConstants.BOARD,
            AuthoritiesConstants.ARCHIVIST
        );
        String[] managers = RoleGroups.tenantManagers();
        managers[0] = AuthoritiesConstants.USER;
        assertThat(RoleGroups.tenantManagers()).doesNotContain(AuthoritiesConstants.USER);
    }

    @Test
    void readsTheRoleOfTheSession() {
        Authentication board = session(AuthoritiesConstants.BOARD);
        Authentication admin = session(AuthoritiesConstants.ADMIN);
        Authentication archivist = session(AuthoritiesConstants.ARCHIVIST);

        assertThat(RoleGroups.isTenantManager(board)).isTrue();
        assertThat(RoleGroups.isTenantAdmin(board)).isFalse();
        assertThat(RoleGroups.isCatalogueManager(board)).isTrue();
        assertThat(RoleGroups.isTenantAdmin(admin)).isTrue();
        assertThat(RoleGroups.isTenantManager(archivist)).isFalse();
        assertThat(RoleGroups.isCatalogueManager(archivist)).isTrue();
        assertThat(RoleGroups.isTenantManager(session(AuthoritiesConstants.USER))).isFalse();
        assertThat(RoleGroups.isTenantManager(null)).isFalse();
    }

    private static Authentication session(String authority) {
        return new TestingAuthenticationToken("user", null, authority);
    }

    private static List<String> names(Set<RoleEnum> roles) {
        return roles.stream().map(RoleEnum::toString).collect(Collectors.toList());
    }
}
