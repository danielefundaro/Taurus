package com.fundaro.zodiac.taurus.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

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

    private String tenant;

    private Set<String> roles;

    private Boolean active;

    private Set<ChildrenEntities> instruments;

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

    public String getTenant() {
        return tenant;
    }

    public void setTenant(String tenant) {
        this.tenant = tenant;
    }

    public Set<String> getRoles() {
        return roles;
    }

    public void setRoles(Set<String> roles) {
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
            Objects.equals(tenant, users.tenant) &&
            Objects.equals(roles, users.roles) &&
            Objects.equals(active, users.active) &&
            Objects.equals(instruments, users.instruments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lastName, birthDate, email, tenant, roles, active, instruments);
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
            ", tenant='" + getTenant() + '\'' +
            ", roles=" + getRoles() +
            ", active=" + getActive() +
            ", description='" + getDescription() + "'" +
            ", instruments=" + getInstruments() +
            '}';
    }
}
