package com.fundaro.zodiac.taurus.config;

import com.fundaro.zodiac.taurus.security.AuthoritiesConstants;
import com.fundaro.zodiac.taurus.utils.keycloak.service.KeycloakService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

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
            keycloakService.ensureClientRole(AuthoritiesConstants.TREASURER, "Tenant finance manager");
        } catch (RuntimeException exception) {
            LOG.warn("Unable to provision the Treasurer role automatically; provisioning will be retried at the next startup", exception);
        }
    }
}
