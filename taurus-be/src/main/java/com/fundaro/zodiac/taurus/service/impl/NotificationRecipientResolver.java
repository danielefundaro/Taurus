package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.domain.notification.NotificationAudienceType;
import com.fundaro.zodiac.taurus.domain.notification.NotificationOutboxAudience;
import com.fundaro.zodiac.taurus.repository.UsersRepository;
import com.fundaro.zodiac.taurus.utils.keycloak.service.KeycloakService;
import java.util.Collection;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class NotificationRecipientResolver {

    private final UsersRepository usersRepository;
    private final KeycloakService keycloakService;

    public NotificationRecipientResolver(UsersRepository usersRepository, KeycloakService keycloakService) {
        this.usersRepository = usersRepository;
        this.keycloakService = keycloakService;
    }

    public Set<String> resolve(Collection<NotificationOutboxAudience> audiences) {
        Set<String> recipients = new LinkedHashSet<>();
        Set<RoleEnum> roles = EnumSet.noneOf(RoleEnum.class);
        boolean allActiveUsers = false;
        for (NotificationOutboxAudience audience : audiences) {
            if (audience.getType() == NotificationAudienceType.ROLE) {
                roles.add(RoleEnum.valueOf(audience.getValue()));
            } else if (audience.getType() == NotificationAudienceType.USER) {
                add(recipients, audience.getValue());
            } else if (audience.getType() == NotificationAudienceType.ALL_ACTIVE_USERS) {
                allActiveUsers = true;
            }
        }
        if (allActiveUsers) usersRepository.findAllActiveKeycloakIds().forEach(value -> add(recipients, value));
        if (!roles.isEmpty()) usersRepository.findActiveKeycloakIdsByRolesIn(roles).forEach(value -> add(recipients, value));
        if (roles.contains(RoleEnum.ROLE_SUPER_ADMIN)) {
            keycloakService.getUsersByClientRoles(RoleEnum.ROLE_SUPER_ADMIN).forEach(user -> add(recipients, user.getId()));
        }
        return recipients;
    }

    private static void add(Set<String> recipients, String userId) {
        if (userId != null && !userId.isBlank()) recipients.add(userId.trim());
    }
}
