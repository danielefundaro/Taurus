package com.fundaro.zodiac.keycloakauthenticator;

import com.fundaro.zodiac.keycloakauthenticator.authentication.TenantRoleSelectorAuthenticatorFactory;
import com.fundaro.zodiac.keycloakauthenticator.otp.TaurusOtpResourceProviderFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class KeycloakAuthenticatorApplicationTests {

    @Test
    void authenticatorFactoryExposesStableProviderMetadata() {
        TenantRoleSelectorAuthenticatorFactory factory = new TenantRoleSelectorAuthenticatorFactory();

        assertThat(factory.getId()).isEqualTo(TenantRoleSelectorAuthenticatorFactory.PROVIDER_ID);
        assertThat(factory.getDisplayType()).isEqualTo("Tenant and Role Selector");
        assertThat(factory.isConfigurable()).isFalse();
        assertThat(factory.create(null)).isSameAs(factory.create(null));
    }

    @Test
    void serviceDescriptorsArePackaged() {
        ClassLoader classLoader = getClass().getClassLoader();

        assertThat(classLoader.getResource("META-INF/services/org.keycloak.authentication.AuthenticatorFactory")).isNotNull();
        assertThat(classLoader.getResource("META-INF/services/org.keycloak.protocol.ProtocolMapper")).isNotNull();
        assertThat(classLoader.getResource("META-INF/services/org.keycloak.services.resource.RealmResourceProviderFactory")).isNotNull();
    }

    @Test
    void otpResourceKeepsItsPath() {
        assertThat(new TaurusOtpResourceProviderFactory().getId()).isEqualTo("taurus-otp");
    }

}
