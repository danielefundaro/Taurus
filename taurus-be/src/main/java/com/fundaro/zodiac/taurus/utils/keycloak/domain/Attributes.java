package com.fundaro.zodiac.taurus.utils.keycloak.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Attributes implements Serializable {
    @JsonProperty("saml.assertion.signature")
    private String assertionSignature;
    @JsonProperty("saml.force.post.binding")
    private String binding;
    @JsonProperty("saml.multivalued.roles")
    private String roles;
    @JsonProperty("saml.encrypt")
    private String encrypt;
    @JsonProperty("post.logout.redirect.uris")
    private String uris;
    @JsonProperty("saml.server.signature")
    private String serverSignature;
    @JsonProperty("saml.server.signature.keyinfo.ext")
    private String keyInfoExt;
    @JsonProperty("exclude.session.state.from.auth.response")
    private String response;
    @JsonProperty("realm_client")
    private String realmClient;
    @JsonProperty("saml_force_name_id_format")
    private String samlForceNameIdFormat;
    @JsonProperty("saml.client.signature")
    private String clientSignature;
    @JsonProperty("tls.client.certificate.bound.access.tokens")
    private String boundAccessTokens;
    @JsonProperty("saml.authnstatement")
    private String authnstatement;
    @JsonProperty("display.on.consent.screen")
    private String displayOnConsentScreen;
    @JsonProperty("saml.onetimeuse.condition")
    private String oneTimeUseCondition;

    public String getAssertionSignature() {
        return assertionSignature;
    }

    public void setAssertionSignature(String assertionSignature) {
        this.assertionSignature = assertionSignature;
    }

    public String getBinding() {
        return binding;
    }

    public void setBinding(String binding) {
        this.binding = binding;
    }

    public String getRoles() {
        return roles;
    }

    public void setRoles(String roles) {
        this.roles = roles;
    }

    public String getEncrypt() {
        return encrypt;
    }

    public void setEncrypt(String encrypt) {
        this.encrypt = encrypt;
    }

    public String getUris() {
        return uris;
    }

    public void setUris(String uris) {
        this.uris = uris;
    }

    public String getServerSignature() {
        return serverSignature;
    }

    public void setServerSignature(String serverSignature) {
        this.serverSignature = serverSignature;
    }

    public String getKeyInfoExt() {
        return keyInfoExt;
    }

    public void setKeyInfoExt(String keyInfoExt) {
        this.keyInfoExt = keyInfoExt;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    public String getRealmClient() {
        return realmClient;
    }

    public void setRealmClient(String realmClient) {
        this.realmClient = realmClient;
    }

    public String getSamlForceNameIdFormat() {
        return samlForceNameIdFormat;
    }

    public void setSamlForceNameIdFormat(String samlForceNameIdFormat) {
        this.samlForceNameIdFormat = samlForceNameIdFormat;
    }

    public String getClientSignature() {
        return clientSignature;
    }

    public void setClientSignature(String clientSignature) {
        this.clientSignature = clientSignature;
    }

    public String getBoundAccessTokens() {
        return boundAccessTokens;
    }

    public void setBoundAccessTokens(String boundAccessTokens) {
        this.boundAccessTokens = boundAccessTokens;
    }

    public String getAuthnstatement() {
        return authnstatement;
    }

    public void setAuthnstatement(String authnstatement) {
        this.authnstatement = authnstatement;
    }

    public String getDisplayOnConsentScreen() {
        return displayOnConsentScreen;
    }

    public void setDisplayOnConsentScreen(String displayOnConsentScreen) {
        this.displayOnConsentScreen = displayOnConsentScreen;
    }

    public String getOneTimeUseCondition() {
        return oneTimeUseCondition;
    }

    @Override
    public String toString() {
        return "Attributes{" +
            "assertionSignature='" + assertionSignature + '\'' +
            ", binding='" + binding + '\'' +
            ", roles='" + roles + '\'' +
            ", encrypt='" + encrypt + '\'' +
            ", uris='" + uris + '\'' +
            ", serverSignature='" + serverSignature + '\'' +
            ", keyInfoExt='" + keyInfoExt + '\'' +
            ", response='" + response + '\'' +
            ", realmClient='" + realmClient + '\'' +
            ", samlForceNameIdFormat='" + samlForceNameIdFormat + '\'' +
            ", clientSignature='" + clientSignature + '\'' +
            ", boundAccessTokens='" + boundAccessTokens + '\'' +
            ", authnstatement='" + authnstatement + '\'' +
            ", displayOnConsentScreen='" + displayOnConsentScreen + '\'' +
            ", oneTimeUseCondition='" + oneTimeUseCondition + '\'' +
            '}';
    }

    public void setOneTimeUseCondition(String oneTimeUseCondition) {
        this.oneTimeUseCondition = oneTimeUseCondition;
    }
}
