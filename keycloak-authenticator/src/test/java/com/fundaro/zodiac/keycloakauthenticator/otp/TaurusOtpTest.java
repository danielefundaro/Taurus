package com.fundaro.zodiac.keycloakauthenticator.otp;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.keycloak.credential.CredentialInput;
import org.keycloak.credential.CredentialModel;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.SubjectCredentialManager;
import org.keycloak.models.UserModel;
import org.keycloak.models.UserProvider;
import org.keycloak.models.credential.OTPCredentialModel;
import org.keycloak.services.managers.AuthenticationManager;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TaurusOtpTest {

    private static final String CLIENT_UUID = "client-uuid";

    private KeycloakSession session;
    private ClientModel client;
    private RoleModel role;
    private UserModel caller;
    private UserModel user;
    private SubjectCredentialManager credentials;
    private AuthenticationManager.AuthResult auth;

    @BeforeEach
    void setUp() {
        session = mock(KeycloakSession.class);
        RealmModel realm = mock(RealmModel.class);
        KeycloakContext context = mock(KeycloakContext.class);
        when(session.getContext()).thenReturn(context);
        when(context.getRealm()).thenReturn(realm);

        client = mock(ClientModel.class);
        when(client.getId()).thenReturn(CLIENT_UUID);
        when(client.isEnabled()).thenReturn(true);
        role = mock(RoleModel.class);
        when(client.getRole(TaurusOtp.VERIFIER_ROLE)).thenReturn(role);
        when(realm.getClientByClientId(TaurusOtp.CLIENT_ID)).thenReturn(client);

        caller = mock(UserModel.class);
        when(caller.getServiceAccountClientLink()).thenReturn(CLIENT_UUID);
        when(caller.hasRole(role)).thenReturn(true);
        auth = new AuthenticationManager.AuthResult(caller, null, null, client);

        user = mock(UserModel.class);
        when(user.getId()).thenReturn("user-1");
        when(user.isEnabled()).thenReturn(true);
        credentials = mock(SubjectCredentialManager.class);
        when(user.credentialManager()).thenReturn(credentials);
        UserProvider users = mock(UserProvider.class);
        when(session.users()).thenReturn(users);
        when(users.getUserById(realm, "user-1")).thenReturn(user);
    }

    @Test
    void refusesRequestsWithoutToken() {
        assertThat(otp(null).status("user-1").status()).isEqualTo(403);
    }

    @Test
    void refusesTheServiceAccountWithoutTheVerifierRole() {
        when(caller.hasRole(role)).thenReturn(false);

        assertThat(otp(auth).status("user-1").status()).isEqualTo(403);
        assertThat(otp(auth).verify("user-1", "123456").status()).isEqualTo(403);
        verify(credentials, never()).isValid(anyList());
    }

    @Test
    void refusesTokensOfOtherClientsAndOfUsers() {
        ClientModel other = mock(ClientModel.class);
        when(other.getId()).thenReturn("other-uuid");
        assertThat(otp(new AuthenticationManager.AuthResult(caller, null, null, other)).status("user-1").status()).isEqualTo(403);

        UserModel person = mock(UserModel.class);
        when(person.hasRole(role)).thenReturn(true);
        assertThat(otp(new AuthenticationManager.AuthResult(person, null, null, client)).status("user-1").status()).isEqualTo(403);
    }

    @Test
    void refusesEveryCallerWhenTheClientIsDisabled() {
        when(client.isEnabled()).thenReturn(false);

        assertThat(otp(auth).status("user-1").status()).isEqualTo(403);
    }

    @Test
    void reportsWhetherTheAppIsConfigured() {
        when(credentials.isConfiguredFor(OTPCredentialModel.TYPE)).thenReturn(true);

        assertThat(otp(auth).status("user-1")).isEqualTo(new TaurusOtp.Outcome(200, true));
    }

    @Test
    void answersNotFoundForUnknownDisabledAndServiceAccountUsers() {
        assertThat(otp(auth).status("missing").status()).isEqualTo(404);
        assertThat(otp(auth).status(" ").status()).isEqualTo(404);

        when(user.isEnabled()).thenReturn(false);
        assertThat(otp(auth).status("user-1").status()).isEqualTo(404);

        when(user.isEnabled()).thenReturn(true);
        when(user.getServiceAccountClientLink()).thenReturn(CLIENT_UUID);
        assertThat(otp(auth).verify("user-1", "123456").status()).isEqualTo(404);
    }

    @Test
    void acceptsAValidCodeOfAnyOtpCredential() {
        when(credentials.getStoredCredentialsByTypeStream(OTPCredentialModel.TYPE)).thenAnswer(invocation -> Stream.of(credential("phone"), credential("tablet")));
        when(credentials.isValid(anyList())).thenAnswer(invocation -> "tablet".equals(invocation.<List<CredentialInput>>getArgument(0).get(0).getCredentialId()));

        assertThat(otp(auth).verify("user-1", "123456").status()).isEqualTo(204);

        ArgumentCaptor<List<CredentialInput>> input = ArgumentCaptor.captor();
        verify(credentials, times(2)).isValid(input.capture());
        List<CredentialInput> checked = input.getAllValues().stream().flatMap(List::stream).toList();
        assertThat(checked).extracting(CredentialInput::getCredentialId).containsExactly("phone", "tablet");
        assertThat(checked).allSatisfy(value -> {
            assertThat(value.getType()).isEqualTo(OTPCredentialModel.TYPE);
            assertThat(value.getChallengeResponse()).isEqualTo("123456");
        });
    }

    @Test
    void refusesWrongAndReusedCodes() {
        when(credentials.getStoredCredentialsByTypeStream(OTPCredentialModel.TYPE)).thenAnswer(invocation -> Stream.of(credential("phone")));
        // The credential provider applies the realm policy: a code is valid only once.
        when(credentials.isValid(anyList())).thenReturn(true, false);

        assertThat(otp(auth).verify("user-1", "123456").status()).isEqualTo(204);
        assertThat(otp(auth).verify("user-1", "123456").status()).isEqualTo(422);
    }

    @Test
    void refusesCodesOfUsersWithoutTheApp() {
        when(credentials.getStoredCredentialsByTypeStream(OTPCredentialModel.TYPE)).thenAnswer(invocation -> Stream.empty());

        assertThat(otp(auth).verify("user-1", "123456").status()).isEqualTo(422);
    }

    @Test
    void refusesMalformedCodesWithoutCheckingThem() {
        assertThat(otp(auth).verify("user-1", "12a456").status()).isEqualTo(422);
        assertThat(otp(auth).verify("user-1", "12345").status()).isEqualTo(422);
        assertThat(otp(auth).verify("user-1", null).status()).isEqualTo(422);
        verify(credentials, never()).getStoredCredentialsByTypeStream(any());
    }

    @Test
    void neverPrintsTheCode() {
        TaurusOtpResource.VerifyRequest request = new TaurusOtpResource.VerifyRequest();
        request.setCode("123456");

        assertThat(request.toString()).doesNotContain("123456");
    }

    private TaurusOtp otp(AuthenticationManager.AuthResult result) {
        return new TaurusOtp(session, current -> result);
    }

    private static CredentialModel credential(String id) {
        CredentialModel credential = new CredentialModel();
        credential.setId(id);
        credential.setType(OTPCredentialModel.TYPE);
        return credential;
    }
}
