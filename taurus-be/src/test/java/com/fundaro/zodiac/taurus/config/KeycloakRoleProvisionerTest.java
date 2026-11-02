package com.fundaro.zodiac.taurus.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;
import com.fundaro.zodiac.taurus.utils.keycloak.service.KeycloakService;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KeycloakRoleProvisionerTest {

    @Test
    void describesEveryRole() {
        Map<String, String> descriptions = KeycloakRoleProvisioner.roleDescriptions();

        assertThat(descriptions.keySet()).containsExactlyElementsOf(Arrays.stream(RoleEnum.values()).map(RoleEnum::toString).toList());
        assertThat(descriptions.values()).allSatisfy(description -> assertThat(description).isNotBlank());
        assertThat(descriptions).containsEntry("ROLE_BOARD", "Tenant board member");
    }

    @Test
    void asksKeycloakForEveryRole() {
        KeycloakService keycloakService = mock(KeycloakService.class);

        new KeycloakRoleProvisioner(keycloakService).provisionRoles();

        verify(keycloakService).ensureClientRoles(KeycloakRoleProvisioner.roleDescriptions());
    }

    @Test
    void doesNotStopTheStartupWhenKeycloakFails() {
        KeycloakService keycloakService = mock(KeycloakService.class);
        when(keycloakService.ensureClientRoles(anyMap())).thenThrow(new IllegalStateException("Keycloak unavailable"));

        assertThatCode(() -> new KeycloakRoleProvisioner(keycloakService).provisionRoles()).doesNotThrowAnyException();
    }

    @Test
    void declaresEveryRoleInTheDevelopmentRealm() throws IOException {
        JsonNode realm = new ObjectMapper().readTree(Path.of("src/main/docker/realm-config/taurus-realm.json").toFile());
        Map<String, String> descriptions = new HashMap<>();
        realm.path("roles").path("client").path("web_app").forEach(role -> descriptions.put(role.path("name").asText(), role.path("description").asText()));

        assertThat(descriptions).containsExactlyInAnyOrderEntriesOf(KeycloakRoleProvisioner.roleDescriptions());
    }
}
