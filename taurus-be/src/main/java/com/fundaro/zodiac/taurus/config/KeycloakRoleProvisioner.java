package com.fundaro.zodiac.taurus.config;

import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.utils.keycloak.service.KeycloakService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Creates in Keycloak the client roles of {@link RoleEnum} that are missing. Without the client role, the roles given to
 * a user would be dropped when saving it. Existing roles are not changed and no role is removed.
 */
@Component
@ConditionalOnProperty(prefix = "application.keycloak", name = "provision-roles", havingValue = "true")
public class KeycloakRoleProvisioner {

    private static final Logger LOG = LoggerFactory.getLogger(KeycloakRoleProvisioner.class);
    private final KeycloakService keycloakService;

    public KeycloakRoleProvisioner(KeycloakService keycloakService) {
        this.keycloakService = keycloakService;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void provisionRoles() {
        try {
            keycloakService.ensureClientRoles(roleDescriptions());
        } catch (RuntimeException exception) {
            LOG.warn("Unable to provision the Keycloak client roles automatically; provisioning will be retried at the next startup", exception);
        }
    }

    static Map<String, String> roleDescriptions() {
        Map<String, String> descriptions = new LinkedHashMap<>();
        for (RoleEnum role : RoleEnum.values()) descriptions.put(role.toString(), role.getKeycloakDescription());
        return descriptions;
    }
}
