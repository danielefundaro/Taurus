package com.fundaro.zodiac.keycloakauthenticator.otp;

import org.jboss.logging.Logger;
import org.keycloak.models.ClientModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserCredentialModel;
import org.keycloak.models.UserModel;
import org.keycloak.models.credential.OTPCredentialModel;
import org.keycloak.services.managers.AuthenticationManager;

import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Checks of {@link TaurusOtpResource}, kept apart from JAX-RS. Only the service account of the confidential client
 * {@value #CLIENT_ID} with its client role {@value #VERIFIER_ROLE} is authorized. Codes are checked against every OTP
 * credential of the user with the realm policy, so a code already used, even for the login, is refused when the policy
 * does not allow reuse. Codes are never logged.
 */
class TaurusOtp {

    static final String CLIENT_ID = "taurus-signature";
    static final String VERIFIER_ROLE = "otp-verifier";

    private static final Logger LOG = Logger.getLogger(TaurusOtp.class);
    private static final Pattern CODE = Pattern.compile("\\d{6,8}");

    private final KeycloakSession session;
    private final Function<KeycloakSession, AuthenticationManager.AuthResult> authenticator;

    TaurusOtp(KeycloakSession session, Function<KeycloakSession, AuthenticationManager.AuthResult> authenticator) {
        this.session = session;
        this.authenticator = authenticator;
    }

    /**
     * Whether the user has configured the authentication app.
     */
    Outcome status(String userId) {
        UserOrStatus target = user(userId);
        if (target.user() == null) return new Outcome(target.status(), null);
        return new Outcome(200, target.user().credentialManager().isConfiguredFor(OTPCredentialModel.TYPE));
    }

    /**
     * Checks a code of the authentication app: {@code 204} when valid, {@code 422} otherwise.
     */
    Outcome verify(String userId, String code) {
        UserOrStatus target = user(userId);
        UserModel user = target.user();
        if (user == null) return new Outcome(target.status(), null);
        boolean valid = code != null && CODE.matcher(code).matches() && user.credentialManager()
            .getStoredCredentialsByTypeStream(OTPCredentialModel.TYPE)
            .anyMatch(credential -> user.credentialManager().isValid(List.of(new UserCredentialModel(credential.getId(), OTPCredentialModel.TYPE, code))));
        LOG.debugf("taurus_otp_verification userId=%s valid=%s", user.getId(), valid);
        return new Outcome(valid ? 204 : 422, null);
    }

    private UserOrStatus user(String userId) {
        RealmModel realm = session.getContext().getRealm();
        if (!authorized(realm)) return new UserOrStatus(null, 403);
        UserModel user = userId == null || userId.isBlank() ? null : session.users().getUserById(realm, userId);
        if (user == null || !user.isEnabled() || user.getServiceAccountClientLink() != null) return new UserOrStatus(null, 404);
        return new UserOrStatus(user, 200);
    }

    /**
     * The caller must be the service account of the signature client, with the verifier role.
     */
    private boolean authorized(RealmModel realm) {
        AuthenticationManager.AuthResult auth = authenticator.apply(session);
        if (auth == null || auth.client() == null || auth.user() == null) return false;
        ClientModel client = realm.getClientByClientId(CLIENT_ID);
        if (client == null || !client.isEnabled() || !client.getId().equals(auth.client().getId())) return false;
        RoleModel role = client.getRole(VERIFIER_ROLE);
        UserModel caller = auth.user();
        return role != null && client.getId().equals(caller.getServiceAccountClientLink()) && caller.hasRole(role);
    }

    /**
     * HTTP status of a check and, for the status of a user, whether the app is configured.
     */
    record Outcome(int status, Boolean configured) {
    }

    private record UserOrStatus(UserModel user, int status) {
    }
}
