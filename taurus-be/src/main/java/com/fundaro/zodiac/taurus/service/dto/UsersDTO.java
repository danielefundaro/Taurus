package com.fundaro.zodiac.taurus.service.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fundaro.zodiac.taurus.domain.enumeration.RoleEnum;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class UsersDTO extends CommonFieldsOpenSearchDTO {

    private String lastName;

    private ZonedDateTime birthDate;

    private String email;

    private Set<RoleEnum> roles;

    private Boolean active;

    private Set<ChildrenEntitiesDTO> instruments;

    @JsonIgnore
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

    public Set<ChildrenEntitiesDTO> getInstruments() {
        return instruments;
    }

    public void setInstruments(Set<ChildrenEntitiesDTO> instruments) {
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
        if (!(o instanceof UsersDTO usersDTO)) {
            return false;
        }

        return super.equals(usersDTO) &&
            Objects.equals(lastName, usersDTO.lastName) &&
            Objects.equals(birthDate, usersDTO.birthDate) &&
            Objects.equals(email, usersDTO.email) &&
            Objects.equals(roles, usersDTO.roles) &&
            Objects.equals(active, usersDTO.active) &&
            Objects.equals(instruments, usersDTO.instruments) &&
            Objects.equals(keycloakId, usersDTO.keycloakId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), lastName, birthDate, email, roles, active, instruments, keycloakId);
    }

    @Override
    public String toString() {
        return "UsersDTO{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", lastName='" + getLastName() + "'" +
            ", description='" + getDescription() + "'" +
            ", birthDate=" + getBirthDate() +
            ", email='" + getEmail() + "'" +
            ", roles=" + getRoles() +
            ", active=" + getActive() +
            ", instruments=" + getInstruments() +
            ", keycloakId=" + getKeycloakId() +
            '}';
    }
}
