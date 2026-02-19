package com.fundaro.zodiac.taurus.utils.keycloak.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Client implements Serializable {
    private String id;
    private String clientId;
    private String rootUrl;
    private String adminUrl;
    private Boolean surrogateAuthRequired;
    private Boolean enabled;
    private Boolean alwaysDisplayInConsole;
    private String clientAuthenticatorType;
    private List<String> redirectUris;
    private List<String> webOrigins;
    private Integer notBefore;
    private Boolean bearerOnly;
    private Boolean consentRequired;
    private Boolean standardFlowEnabled;
    private Boolean implicitFlowEnabled;
    private Boolean directAccessGrantsEnabled;
    private Boolean serviceAccountsEnabled;
    private Boolean privateClient;
    @JsonProperty("frontchannelLogout")
    private Boolean frontChannelLogout;
    private String protocol;
    private Attributes attributes;
    private Object authenticationFlowBindingOverrides;
    private Boolean fullScopeAllowed;
    private Integer nodeReRegistrationTimeout;
    private List<String> defaultClientScopes;
    private List<String> optionalClientScopes;
    private Access access;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(String clientId) {
        this.clientId = clientId;
    }

    public String getRootUrl() {
        return rootUrl;
    }

    public void setRootUrl(String rootUrl) {
        this.rootUrl = rootUrl;
    }

    public String getAdminUrl() {
        return adminUrl;
    }

    public void setAdminUrl(String adminUrl) {
        this.adminUrl = adminUrl;
    }

    public Boolean getSurrogateAuthRequired() {
        return surrogateAuthRequired;
    }

    public void setSurrogateAuthRequired(Boolean surrogateAuthRequired) {
        this.surrogateAuthRequired = surrogateAuthRequired;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getAlwaysDisplayInConsole() {
        return alwaysDisplayInConsole;
    }

    public void setAlwaysDisplayInConsole(Boolean alwaysDisplayInConsole) {
        this.alwaysDisplayInConsole = alwaysDisplayInConsole;
    }

    public String getClientAuthenticatorType() {
        return clientAuthenticatorType;
    }

    public void setClientAuthenticatorType(String clientAuthenticatorType) {
        this.clientAuthenticatorType = clientAuthenticatorType;
    }

    public List<String> getRedirectUris() {
        return redirectUris;
    }

    public void setRedirectUris(List<String> redirectUris) {
        this.redirectUris = redirectUris;
    }

    public List<String> getWebOrigins() {
        return webOrigins;
    }

    public void setWebOrigins(List<String> webOrigins) {
        this.webOrigins = webOrigins;
    }

    public Integer getNotBefore() {
        return notBefore;
    }

    public void setNotBefore(Integer notBefore) {
        this.notBefore = notBefore;
    }

    public Boolean getBearerOnly() {
        return bearerOnly;
    }

    public void setBearerOnly(Boolean bearerOnly) {
        this.bearerOnly = bearerOnly;
    }

    public Boolean getConsentRequired() {
        return consentRequired;
    }

    public void setConsentRequired(Boolean consentRequired) {
        this.consentRequired = consentRequired;
    }

    public Boolean getStandardFlowEnabled() {
        return standardFlowEnabled;
    }

    public void setStandardFlowEnabled(Boolean standardFlowEnabled) {
        this.standardFlowEnabled = standardFlowEnabled;
    }

    public Boolean getImplicitFlowEnabled() {
        return implicitFlowEnabled;
    }

    public void setImplicitFlowEnabled(Boolean implicitFlowEnabled) {
        this.implicitFlowEnabled = implicitFlowEnabled;
    }

    public Boolean getDirectAccessGrantsEnabled() {
        return directAccessGrantsEnabled;
    }

    public void setDirectAccessGrantsEnabled(Boolean directAccessGrantsEnabled) {
        this.directAccessGrantsEnabled = directAccessGrantsEnabled;
    }

    public Boolean getServiceAccountsEnabled() {
        return serviceAccountsEnabled;
    }

    public void setServiceAccountsEnabled(Boolean serviceAccountsEnabled) {
        this.serviceAccountsEnabled = serviceAccountsEnabled;
    }

    public Boolean getPrivateClient() {
        return privateClient;
    }

    public void setPrivateClient(Boolean privateClient) {
        this.privateClient = privateClient;
    }

    public Boolean getFrontChannelLogout() {
        return frontChannelLogout;
    }

    public void setFrontChannelLogout(Boolean frontChannelLogout) {
        this.frontChannelLogout = frontChannelLogout;
    }

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public Attributes getAttributes() {
        return attributes;
    }

    public void setAttributes(Attributes attributes) {
        this.attributes = attributes;
    }

    public Object getAuthenticationFlowBindingOverrides() {
        return authenticationFlowBindingOverrides;
    }

    public void setAuthenticationFlowBindingOverrides(Object authenticationFlowBindingOverrides) {
        this.authenticationFlowBindingOverrides = authenticationFlowBindingOverrides;
    }

    public Boolean getFullScopeAllowed() {
        return fullScopeAllowed;
    }

    public void setFullScopeAllowed(Boolean fullScopeAllowed) {
        this.fullScopeAllowed = fullScopeAllowed;
    }

    public Integer getNodeReRegistrationTimeout() {
        return nodeReRegistrationTimeout;
    }

    public void setNodeReRegistrationTimeout(Integer nodeReRegistrationTimeout) {
        this.nodeReRegistrationTimeout = nodeReRegistrationTimeout;
    }

    public List<String> getDefaultClientScopes() {
        return defaultClientScopes;
    }

    public void setDefaultClientScopes(List<String> defaultClientScopes) {
        this.defaultClientScopes = defaultClientScopes;
    }

    public List<String> getOptionalClientScopes() {
        return optionalClientScopes;
    }

    public void setOptionalClientScopes(List<String> optionalClientScopes) {
        this.optionalClientScopes = optionalClientScopes;
    }

    public Access getAccess() {
        return access;
    }

    public void setAccess(Access access) {
        this.access = access;
    }

    @Override
    public String toString() {
        return "Client{" +
            "id='" + id + '\'' +
            ", clientId='" + clientId + '\'' +
            ", rootUrl='" + rootUrl + '\'' +
            ", adminUrl='" + adminUrl + '\'' +
            ", surrogateAuthRequired=" + surrogateAuthRequired +
            ", enabled=" + enabled +
            ", alwaysDisplayInConsole=" + alwaysDisplayInConsole +
            ", clientAuthenticatorType='" + clientAuthenticatorType + '\'' +
            ", redirectUris=" + redirectUris +
            ", webOrigins=" + webOrigins +
            ", notBefore=" + notBefore +
            ", bearerOnly=" + bearerOnly +
            ", consentRequired=" + consentRequired +
            ", standardFlowEnabled=" + standardFlowEnabled +
            ", implicitFlowEnabled=" + implicitFlowEnabled +
            ", directAccessGrantsEnabled=" + directAccessGrantsEnabled +
            ", serviceAccountsEnabled=" + serviceAccountsEnabled +
            ", privateClient=" + privateClient +
            ", frontChannelLogout=" + frontChannelLogout +
            ", protocol='" + protocol + '\'' +
            ", attributes=" + attributes +
            ", authenticationFlowBindingOverrides=" + authenticationFlowBindingOverrides +
            ", fullScopeAllowed=" + fullScopeAllowed +
            ", nodeReRegistrationTimeout=" + nodeReRegistrationTimeout +
            ", defaultClientScopes=" + defaultClientScopes +
            ", optionalClientScopes=" + optionalClientScopes +
            ", access=" + access +
            '}';
    }
}
