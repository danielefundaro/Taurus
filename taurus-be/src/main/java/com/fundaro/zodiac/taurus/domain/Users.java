package com.fundaro.zodiac.taurus.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Set;

/**
 * A Users.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Users extends CommonFieldsOpenSearch {

    @JsonProperty("last_name")
    private String lastName;

    @JsonProperty("birth_date")
    private ZonedDateTime birthDate;

    private String email;

    private Set<ChildrenEntities> tenants;

    private Set<RoleEnum> roles;

    private Boolean active;

    private Set<ChildrenEntities> instruments;

    private String keycloakId;

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public ZonedDateTime getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(ZonedDateTime birthDate) {
        this.birthDate = birthDate;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Set<ChildrenEntities> getTenants() {
        return tenants;
    }

    public void setTenants(Set<ChildrenEntities> tenants) {
        this.tenants = tenants;
    }

    public Set<RoleEnum> getRoles() {
        return roles;
    }

    public void setRoles(Set<RoleEnum> roles) {
        this.roles = roles;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Set<ChildrenEntities> getInstruments() {
        return instruments;
    }

    public void setInstruments(Set<ChildrenEntities> instruments) {
        this.instruments = instruments;
    }

    public String getKeycloakId() {
        return keycloakId;
    }

    public void setKeycloakId(String keycloakId) {
        this.keycloakId = keycloakId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Users users)) {
            return false;
        }
        return super.equals(users) &&
            Objects.equals(lastName, users.lastName) &&
            Objects.equals(birthDate, users.birthDate) &&
            Objects.equals(email, users.email) &&
            Objects.equals(tenants, users.tenants) &&
            Objects.equals(roles, users.roles) &&
            Objects.equals(active, users.active) &&
            Objects.equals(instruments, users.instruments) &&
            Objects.equals(keycloakId, users.keycloakId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lastName, birthDate, email, tenants, roles, active, instruments, keycloakId);
    }

    @Override
    public String toString() {
        return "Users{" +
            "id=" + getId() +
            ", deleted='" + getDeleted() + "'" +
            ", insertBy='" + getInsertBy() + "'" +
            ", insertDate='" + getInsertDate() + "'" +
            ", editBy='" + getEditBy() + "'" +
            ", editDate='" + getEditDate() + "'" +
            ", name='" + getName() + "'" +
            ", lastName='" + getLastName() + '\'' +
            ", birthDate=" + getBirthDate() +
            ", email='" + getEmail() + '\'' +
            ", tenant='" + getTenants() + '\'' +
            ", roles=" + getRoles() +
            ", active=" + getActive() +
            ", description='" + getDescription() + "'" +
            ", instruments=" + getInstruments() +
            ", keycloakId='" + getKeycloakId() + "'" +
            '}';
    }
}
