package com.fundaro.zodiac.taurus.domain.criteria;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fundaro.zodiac.taurus.domain.criteria.filter.DateFilter;
import org.springdoc.core.annotations.ParameterObject;
import tech.jhipster.service.filter.BooleanFilter;
import tech.jhipster.service.filter.StringFilter;

import java.util.Objects;
import java.util.Optional;

@ParameterObject
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UsersCriteria extends CommonOpenSearchCriteria {

    @JsonProperty("last_name")
    private StringFilter lastName;

    @JsonProperty("birth_date")
    private DateFilter birthDate;

    private StringFilter email;

    private StringFilter roles;

    private BooleanFilter active;

    private StringFilter instrumentId;

    public UsersCriteria() {
        super();
    }

    public UsersCriteria(UsersCriteria other) {
        super(other);
        this.lastName = other.optionalLastName().map(StringFilter::copy).orElse(null);
        this.birthDate = other.optionalBirthDate().map(DateFilter::copy).orElse(null);
        this.email = other.optionalEmail().map(StringFilter::copy).orElse(null);
        this.roles = other.optionalRoles().map(StringFilter::copy).orElse(null);
        this.active = other.optionalActive().map(BooleanFilter::copy).orElse(null);
        this.instrumentId = other.optionalInstrumentId().map(StringFilter::copy).orElse(null);
    }

    @Override
    public UsersCriteria copy() {
        return new UsersCriteria(this);
    }

    public StringFilter getLastName() {
        return lastName;
    }

    public Optional<StringFilter> optionalLastName() {
        return Optional.ofNullable(lastName);
    }

    public void setLastName(StringFilter lastName) {
        this.lastName = lastName;
    }

    public DateFilter getBirthDate() {
        return birthDate;
    }

    public Optional<DateFilter> optionalBirthDate() {
        return Optional.ofNullable(birthDate);
    }

    public void setBirthDate(DateFilter birthDate) {
        this.birthDate = birthDate;
    }

    public StringFilter getEmail() {
        return email;
    }

    public Optional<StringFilter> optionalEmail() {
        return Optional.ofNullable(email);
    }

    public void setEmail(StringFilter email) {
        this.email = email;
    }

    public StringFilter getRoles() {
        return roles;
    }

    public Optional<StringFilter> optionalRoles() {
        return Optional.ofNullable(roles);
    }

    public void setRoles(StringFilter roles) {
        this.roles = roles;
    }

    public BooleanFilter getActive() {
        return active;
    }

    public Optional<BooleanFilter> optionalActive() {
        return Optional.ofNullable(active);
    }

    public void setActive(BooleanFilter active) {
        this.active = active;
    }

    public StringFilter getInstrumentId() {
        return instrumentId;
    }

    public Optional<StringFilter> optionalInstrumentId() {
        return Optional.ofNullable(instrumentId);
    }

    public void setInstrumentId(StringFilter instrumentId) {
        this.instrumentId = instrumentId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final UsersCriteria that = (UsersCriteria) o;
        return (
            super.equals(o) &&
                Objects.equals(lastName, that.lastName) &&
                Objects.equals(birthDate, that.birthDate) &&
                Objects.equals(email, that.email) &&
                Objects.equals(roles, that.roles) &&
                Objects.equals(active, that.active) &&
                Objects.equals(instrumentId, that.instrumentId)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), lastName, birthDate, email, roles, active, instrumentId);
    }

    @Override
    public String toString() {
        return "UsersCriteria{" +
            optionalId().map(f -> "id=" + f + ", ").orElse("") +
            optionalName().map(f -> "name=" + f + ", ").orElse("") +
            optionalDescription().map(f -> "description=" + f + ", ").orElse("") +
            optionalLastName().map(f -> "last_name=" + f + ", ").orElse("") +
            optionalBirthDate().map(f -> "birth_date=" + f + ", ").orElse("") +
            optionalEmail().map(f -> "email=" + f + ", ").orElse("") +
            optionalRoles().map(f -> "roles=" + f + ", ").orElse("") +
            optionalActive().map(f -> "active=" + f + ", ").orElse("") +
            optionalInstrumentId().map(f -> "instrument_id=" + f + ", ").orElse("") +
            "}";
    }
}
