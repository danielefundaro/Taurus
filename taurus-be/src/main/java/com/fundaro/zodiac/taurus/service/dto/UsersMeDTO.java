package com.fundaro.zodiac.taurus.service.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Email;

import java.io.Serializable;

/**
 * Minimal DTO for self-service profile updates: only the fields a user can modify on their own account.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UsersMeDTO implements Serializable {

    private String name;

    private String lastName;

    @Email
    private String email;

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

    @Override
    public String toString() {
        return "UsersMeDTO{" +
            "name='" + name + "'" +
            ", lastName='" + lastName + "'" +
            ", email='" + email + "'" +
            '}';
    }
}
