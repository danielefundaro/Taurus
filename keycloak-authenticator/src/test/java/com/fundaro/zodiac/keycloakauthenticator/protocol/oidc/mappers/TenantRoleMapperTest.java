package com.fundaro.zodiac.keycloakauthenticator.protocol.oidc.mappers;

import com.fundaro.zodiac.keycloakauthenticator.authentication.TenantRoleSelectorAuthenticator;
import org.junit.jupiter.api.Test;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.representations.IDToken;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TenantRoleMapperTest {

    private final TenantRoleMapper mapper = new TenantRoleMapper();

    @Test
    void addsTheRoleChoiceClaimOnlyWhenTheRoleWasChosenAmongMore() {
        assertThat(claims("true")).containsEntry("tenant", "ORCHESTRA_A").containsEntry("role", "ROLE_TREASURER")
            .containsEntry("role_choice", true);
        assertThat(claims("false")).containsEntry("role", "ROLE_TREASURER").doesNotContainKey("role_choice");
        // Sessioni aperte prima dell'aggiornamento del provider
        assertThat(claims(null)).containsEntry("role", "ROLE_TREASURER").doesNotContainKey("role_choice");
    }

    private Map<String, Object> claims(String roleChoice) {
        ProtocolMapperModel model = new ProtocolMapperModel();
        model.setConfig(Map.of("tenant.claim.name", "tenant", "role.claim.name", "role"));
        UserSessionModel userSession = mock(UserSessionModel.class);
        when(userSession.getNote("selected_tenant")).thenReturn("ORCHESTRA_A");
        when(userSession.getNote("selected_role")).thenReturn("ROLE_TREASURER");
        when(userSession.getNote(TenantRoleSelectorAuthenticator.ROLE_CHOICE_NOTE)).thenReturn(roleChoice);
        IDToken token = new IDToken();
        mapper.setClaim(token, model, userSession, null, null);
        return token.getOtherClaims();
    }
}
