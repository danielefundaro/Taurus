package com.fundaro.zodiac.taurus.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.repository.UsersRepository;
import com.fundaro.zodiac.taurus.utils.keycloak.domain.User;
import com.fundaro.zodiac.taurus.utils.keycloak.service.KeycloakService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FinanceNotificationRecipientServiceTest {

    @Mock UsersRepository usersRepository;
    @Mock KeycloakService keycloakService;

    @Test
    void combinesTenantRecipientsAndGlobalSuperAdminsWithoutDuplicates() {
        User existingSuperAdmin = user("admin-1");
        User globalSuperAdmin = user("superadmin-1");
        User invalidUser = user(" ");
        when(usersRepository.findActiveKeycloakIdsByRolesIn(anyCollection())).thenReturn(List.of("admin-1", "treasurer-1"));
        when(keycloakService.getUsersByClientRoles(RoleEnum.ROLE_SUPER_ADMIN)).thenReturn(
            List.of(existingSuperAdmin, globalSuperAdmin, invalidUser)
        );

        var recipients = new FinanceNotificationRecipientService(usersRepository, keycloakService).findRecipientIds(
            Set.of(RoleEnum.ROLE_ADMIN, RoleEnum.ROLE_SUPER_ADMIN, RoleEnum.ROLE_TREASURER)
        );

        assertThat(recipients).containsExactly("admin-1", "treasurer-1", "superadmin-1");
    }

    private static User user(String id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
