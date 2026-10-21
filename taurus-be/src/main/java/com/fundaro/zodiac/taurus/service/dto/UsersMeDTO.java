package com.fundaro.zodiac.taurus.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Past;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * Minimal DTO for self-service profile updates: only the fields a user can modify on their own account. The date of
 * birth is kept only in Taurus, the other fields also in Keycloak.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UsersMeDTO implements Serializable {

    private String name;

    private String lastName;

    @Email
    private String email;

    /** Needed in the documents the user signs, for example the loan sheets of the inventory. */
    @Past
    private LocalDate birthDate;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    @Override
    public String toString() {
        return "UsersMeDTO{" +
            "name='" + name + "'" +
            ", lastName='" + lastName + "'" +
            ", email='" + email + "'" +
            '}';
    }
}
