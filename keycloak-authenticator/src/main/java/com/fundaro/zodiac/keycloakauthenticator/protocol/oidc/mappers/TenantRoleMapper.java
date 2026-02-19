package com.fundaro.zodiac.keycloakauthenticator.protocol.oidc.mappers;

import org.keycloak.models.ClientSessionContext;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.ProtocolMapperModel;
import org.keycloak.models.UserSessionModel;
import org.keycloak.protocol.oidc.mappers.*;
import org.keycloak.provider.ProviderConfigProperty;
import org.keycloak.representations.IDToken;

import java.util.ArrayList;
import java.util.List;

public class TenantRoleMapper extends AbstractOIDCProtocolMapper implements OIDCAccessTokenMapper, OIDCIDTokenMapper, UserInfoTokenMapper {

    private static final String PROVIDER_ID = "tenant-role-mapper";
    private static final List<ProviderConfigProperty> configProperties = new ArrayList<>();

    static {
        // Puoi aggiungere proprietà configurabili se necessario
        ProviderConfigProperty property;

        property = new ProviderConfigProperty();
        property.setName("tenant.claim.name");
        property.setLabel("Tenant Claim Name");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        property.setDefaultValue("tenant");
        property.setHelpText("Name of the claim for tenant in the token");
        configProperties.add(property);

        property = new ProviderConfigProperty();
        property.setName("role.claim.name");
        property.setLabel("Role Claim Name");
        property.setType(ProviderConfigProperty.STRING_TYPE);
        property.setDefaultValue("tenant_role");
        property.setHelpText("Name of the claim for role in the token");
        configProperties.add(property);

        // Aggiungi i checkbox standard per access token, id token, userinfo
        OIDCAttributeMapperHelper.addIncludeInTokensConfig(configProperties, TenantRoleMapper.class);
    }

    @Override
    public String getDisplayCategory() {
        return TOKEN_MAPPER_CATEGORY;
    }

    @Override
    public String getDisplayType() {
        return "Tenant and Role Mapper";
    }

    @Override
    public String getHelpText() {
        return "Adds tenant and role selected during authentication to the token";
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        return configProperties;
    }

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    protected void setClaim(IDToken token, ProtocolMapperModel mappingModel,
                            UserSessionModel userSession, KeycloakSession keycloakSession,
                            ClientSessionContext clientSessionCtx) {

        String tenantClaimName = mappingModel.getConfig().getOrDefault("tenant.claim.name", "tenant");
        String roleClaimName = mappingModel.getConfig().getOrDefault("role.claim.name", "tenant_role");

        // Recupera i valori dalle note della sessione utente
        String tenant = userSession.getNote("selected_tenant");
        String role = userSession.getNote("selected_role");

        if (tenant != null && !tenant.isEmpty()) {
            token.getOtherClaims().put(tenantClaimName, tenant);
        }

        if (role != null && !role.isEmpty()) {
            token.getOtherClaims().put(roleClaimName, List.of(role));
        }
    }
}
