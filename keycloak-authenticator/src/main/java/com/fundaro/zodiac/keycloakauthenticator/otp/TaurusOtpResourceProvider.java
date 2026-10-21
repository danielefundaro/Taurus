package com.fundaro.zodiac.keycloakauthenticator.otp;

import org.keycloak.models.KeycloakSession;
import org.keycloak.services.resource.RealmResourceProvider;

/**
 * Exposes {@link TaurusOtpResource} under {@code /realms/{realm}/taurus-otp}.
 */
public class TaurusOtpResourceProvider implements RealmResourceProvider {

    private final KeycloakSession session;

    public TaurusOtpResourceProvider(KeycloakSession session) {
        this.session = session;
    }

    @Override
    public Object getResource() {
        return new TaurusOtpResource(session);
    }

    @Override
    public void close() {
    }
}
