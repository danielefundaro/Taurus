package com.fundaro.zodiac.taurus.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fundaro.zodiac.taurus.domain.ChildrenEntities;

import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class UsersDTO extends CommonFieldsOpenSearchDTO {

    @JsonProperty("last_name")
    private String lastName;

    @JsonProperty("birth_date")
    private ZonedDateTime birthDate;

    private String email;

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
        if (!(o instanceof UsersDTO usersDTO)) {
            return false;
        }

        return super.equals(usersDTO) &&
            Objects.equals(lastName, usersDTO.lastName) &&
            Objects.equals(birthDate, usersDTO.birthDate) &&
            Objects.equals(email, usersDTO.email) &&
            Objects.equals(roles, usersDTO.roles) &&
            Objects.equals(active, usersDTO.active) &&
            Objects.equals(instruments, usersDTO.instruments);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), lastName, birthDate, email, roles, active, instruments);
    }

    @Override
    public String toString() {
        return "UsersDTO{" +
            "id=" + getId() +
            ", name='" + getName() + "'" +
            ", description='" + getDescription() + "'" +
            ", lastName='" + getLastName() + '\'' +
            ", birthDate=" + getBirthDate() +
            ", email='" + getEmail() + '\'' +
            ", roles=" + getRoles() +
            ", active=" + getActive() +
            ", instruments=" + getInstruments() +
            '}';
    }
}
