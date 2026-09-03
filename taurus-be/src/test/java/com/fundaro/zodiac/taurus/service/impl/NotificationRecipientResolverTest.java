package com.fundaro.zodiac.taurus.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.domain.notification.NotificationAudienceType;
import com.fundaro.zodiac.taurus.domain.notification.NotificationOutboxAudience;
import com.fundaro.zodiac.taurus.repository.UsersRepository;
import com.fundaro.zodiac.taurus.utils.keycloak.domain.User;
import com.fundaro.zodiac.taurus.utils.keycloak.service.KeycloakService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationRecipientResolverTest {

    @Mock UsersRepository usersRepository;
    @Mock KeycloakService keycloakService;

    @Test
    void combinesRolesDirectUsersAndGlobalSuperAdminsWithoutDuplicates() {
        when(usersRepository.findActiveKeycloakIdsByRolesIn(anyCollection())).thenReturn(List.of("admin-1", "treasurer-1"));
        when(keycloakService.getUsersByClientRoles(RoleEnum.ROLE_SUPER_ADMIN)).thenReturn(List.of(user("admin-1"), user("superadmin-1"), user(" ")));

        var recipients = new NotificationRecipientResolver(usersRepository, keycloakService).resolve(List.of(
            audience(NotificationAudienceType.ROLE, "ROLE_ADMIN"),
            audience(NotificationAudienceType.ROLE, "ROLE_SUPER_ADMIN"),
            audience(NotificationAudienceType.USER, "direct-user")
        ));

        assertThat(recipients).containsExactly("direct-user", "admin-1", "treasurer-1", "superadmin-1");
    }

    @Test
    void resolvesAllActiveUsers() {
        when(usersRepository.findAllActiveKeycloakIds()).thenReturn(List.of("user-1", "user-2"));

        var recipients = new NotificationRecipientResolver(usersRepository, keycloakService).resolve(
            List.of(audience(NotificationAudienceType.ALL_ACTIVE_USERS, "*"))
        );

        assertThat(recipients).containsExactly("user-1", "user-2");
    }

    private static NotificationOutboxAudience audience(NotificationAudienceType type, String value) {
        NotificationOutboxAudience audience = new NotificationOutboxAudience();
        audience.setType(type);
        audience.setValue(value);
        return audience;
    }

    private static User user(String id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
