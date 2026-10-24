package com.fundaro.zodiac.keycloakauthenticator.authentication;

import jakarta.ws.rs.core.MultivaluedHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.http.HttpRequest;
import org.keycloak.models.GroupModel;
import org.keycloak.models.UserModel;
import org.keycloak.sessions.AuthenticationSessionModel;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TenantRoleSelectorAuthenticatorTest {

    private final TenantRoleSelectorAuthenticator authenticator = new TenantRoleSelectorAuthenticator();

    private AuthenticationFlowContext context;
    private AuthenticationSessionModel authSession;
    private UserModel user;

    @BeforeEach
    void setUp() {
        context = mock(AuthenticationFlowContext.class);
        authSession = mock(AuthenticationSessionModel.class);
        user = mock(UserModel.class);
        when(context.getUser()).thenReturn(user);
        when(context.getAuthenticationSession()).thenReturn(authSession);
    }

    @Test
    void marksNoRoleChoiceWhenTheOnlyTenantHasOneRole() {
        givenTenants(Map.of("ORCHESTRA_A", "ROLE_USER"));

        authenticator.authenticate(context);

        verify(authSession).setUserSessionNote("selected_tenant", "ORCHESTRA_A");
        verify(authSession).setUserSessionNote("selected_role", "ROLE_USER");
        verify(authSession).setUserSessionNote(TenantRoleSelectorAuthenticator.ROLE_CHOICE_NOTE, "false");
        verify(context).success();
    }

    @Test
    void marksTheRoleChoiceWhenTheSelectedTenantOffersMoreRoles() {
        givenTenants(Map.of("ORCHESTRA_A", "ROLE_ADMIN,ROLE_TREASURER"));
        givenForm("ORCHESTRA_A", "ROLE_TREASURER");

        authenticator.action(context);

        verify(authSession).setUserSessionNote("selected_role", "ROLE_TREASURER");
        verify(authSession).setUserSessionNote(TenantRoleSelectorAuthenticator.ROLE_CHOICE_NOTE, "true");
        verify(context).success();
    }

    @Test
    void marksNoRoleChoiceWhenTheSelectedTenantHasOneRoleAmongMoreTenants() {
        givenTenants(Map.of("ORCHESTRA_A", "ROLE_ADMIN,ROLE_TREASURER", "ORCHESTRA_B", "ROLE_USER"));
        givenForm("ORCHESTRA_B", null);

        authenticator.action(context);

        verify(authSession).setUserSessionNote("selected_role", "ROLE_USER");
        verify(authSession).setUserSessionNote(TenantRoleSelectorAuthenticator.ROLE_CHOICE_NOTE, "false");
        verify(authSession, never()).setUserSessionNote(TenantRoleSelectorAuthenticator.ROLE_CHOICE_NOTE, "true");
        verify(context).success();
    }

    private void givenTenants(Map<String, String> rolesByTenant) {
        when(user.getGroupsStream()).thenAnswer(invocation -> rolesByTenant.keySet().stream().sorted().map(name -> {
            GroupModel group = mock(GroupModel.class);
            when(group.getName()).thenReturn(name);
            return group;
        }));
        when(user.getAttributeStream(any())).thenReturn(Stream.empty());
        rolesByTenant.forEach((tenant, roles) ->
            when(user.getAttributeStream(tenant + "_roles")).thenAnswer(invocation -> Arrays.stream(new String[] { roles })));
    }

    private void givenForm(String tenant, String role) {
        MultivaluedHashMap<String, String> form = new MultivaluedHashMap<>();
        form.putSingle("tenant", tenant);
        if (role != null) {
            form.putSingle("role", role);
        }
        HttpRequest request = mock(HttpRequest.class);
        when(request.getDecodedFormParameters()).thenReturn(form);
        when(context.getHttpRequest()).thenReturn(request);
    }
}
