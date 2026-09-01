package com.fundaro.zodiac.taurus.service.impl;

import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.repository.UsersRepository;
import com.fundaro.zodiac.taurus.utils.keycloak.service.KeycloakService;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class FinanceNotificationRecipientService {

    private final UsersRepository usersRepository;
    private final KeycloakService keycloakService;

    public FinanceNotificationRecipientService(UsersRepository usersRepository, KeycloakService keycloakService) {
        this.usersRepository = usersRepository;
        this.keycloakService = keycloakService;
    }

    public Set<String> findRecipientIds(Set<RoleEnum> roles) {
        Set<String> recipients = new LinkedHashSet<>(usersRepository.findActiveKeycloakIdsByRolesIn(roles));
        if (roles.contains(RoleEnum.ROLE_SUPER_ADMIN)) {
            keycloakService.getUsersByClientRoles(RoleEnum.ROLE_SUPER_ADMIN).stream()
                .map(user -> user.getId())
                .filter(userId -> userId != null && !userId.isBlank())
                .forEach(recipients::add);
        }
        return recipients;
    }
}
