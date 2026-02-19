package com.fundaro.zodiac.taurus.utils.keycloak.domain;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class User implements Serializable {

    private String id;

    private String username;

    private String firstName;

    private String lastName;

    private String email;

    private Set<String> groups;

    private Boolean enabled;

    private Boolean totp;

    private Boolean emailVerified;

    private Map<String, List<String>> attributes;

    public User() {
        this.enabled = true;
        this.totp = false;
        this.emailVerified = true;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Set<String> getGroups() {
        return groups;
    }

    public void setGroups(Set<String> groups) {
        this.groups = groups;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Boolean getTotp() {
        return totp;
    }

    public void setTotp(Boolean totp) {
        this.totp = totp;
    }

    public Boolean getEmailVerified() {
        return emailVerified;
    }

    public void setEmailVerified(Boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    public Map<String, List<String>> getAttributes() {
        return attributes;
    }

    public void setAttributes(Map<String, List<String>> attributes) {
        this.attributes = attributes;
    }

    @Override
    public String toString() {
        return "User{" +
            "username='" + username + "'" +
            ", firstName='" + firstName + "'" +
            ", lastName='" + lastName + "'" +
            ", email='" + email + "'" +
            ", groups=" + groups +
            ", enabled=" + enabled +
            ", totp=" + totp +
            ", emailVerified=" + emailVerified +
            ", attributes=" + attributes +
            "}";
    }
}
