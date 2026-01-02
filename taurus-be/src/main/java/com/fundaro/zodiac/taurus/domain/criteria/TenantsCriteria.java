package com.fundaro.zodiac.taurus.domain.criteria;

import org.springdoc.core.annotations.ParameterObject;
import tech.jhipster.service.filter.BooleanFilter;
import tech.jhipster.service.filter.Filter;
import tech.jhipster.service.filter.StringFilter;

import java.util.Objects;
import java.util.Optional;

/**
 * Criteria class for the {@link com.fundaro.zodiac.taurus.domain.Tenants} entity. This class is used
 * in {@link com.fundaro.zodiac.taurus.web.rest.TenantsResource} to receive all the possible filtering options from
 * the Http GET request parameters.
 * For example the following could be a valid request:
 * {@code /tenants?id.greaterThan=5&attr1.contains=something&attr2.specified=false}
 * As Spring is unable to properly convert the types, unless specific {@link Filter} class are used, we need to use
 * fix type specific filters.
 */
@ParameterObject
@SuppressWarnings("common-java:DuplicatedBlocks")
public class TenantsCriteria extends CommonOpenSearchCriteria {

    private StringFilter code;

    private StringFilter email;

    private StringFilter domain;

    private BooleanFilter active;

    public TenantsCriteria() {
        super();
    }

    public TenantsCriteria(TenantsCriteria other) {
        super(other);
        this.code = other.optionalCode().map(StringFilter::copy).orElse(null);
        this.email = other.optionalEmail().map(StringFilter::copy).orElse(null);
        this.domain = other.optionalDomain().map(StringFilter::copy).orElse(null);
        this.active = other.optionalActive().map(BooleanFilter::copy).orElse(null);
    }

    @Override
    public TenantsCriteria copy() {
        return new TenantsCriteria(this);
    }

    public StringFilter getCode() {
        return code;
    }

    public Optional<StringFilter> optionalCode() {
        return Optional.ofNullable(code);
    }

    public TenantsCriteria setCode(StringFilter code) {
        this.code = code;
        return this;
    }

    public StringFilter getEmail() {
        return email;
    }

    public Optional<StringFilter> optionalEmail() {
        return Optional.ofNullable(email);
    }

    public TenantsCriteria setEmail(StringFilter email) {
        this.email = email;
        return this;
    }

    public StringFilter getDomain() {
        return domain;
    }

    public Optional<StringFilter> optionalDomain() {
        return Optional.ofNullable(domain);
    }

    public TenantsCriteria setDomain(StringFilter domain) {
        this.domain = domain;
        return this;
    }

    public BooleanFilter getActive() {
        return active;
    }

    public Optional<BooleanFilter> optionalActive() {
        return Optional.ofNullable(active);
    }

    public TenantsCriteria setActive(BooleanFilter active) {
        this.active = active;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TenantsCriteria that = (TenantsCriteria) o;
        return super.equals(o) &&
            Objects.equals(code, that.code) &&
            Objects.equals(email, that.email) &&
            Objects.equals(domain, that.domain) &&
            Objects.equals(active, that.active);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), code, email, domain, active);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "TenantsCriteria{" +
            optionalId().map(f -> "id=" + f + ", ").orElse("") +
            optionalName().map(f -> "name=" + f + ", ").orElse("") +
            optionalDescription().map(f -> "description=" + f + ", ").orElse("") +
            optionalCode().map(f -> "code=" + f + ", ").orElse("") +
            optionalEmail().map(f -> "email=" + f + ", ").orElse("") +
            optionalDomain().map(f -> "domain=" + f + ", ").orElse("") +
            optionalActive().map(f -> "active=" + f + ", ").orElse("") +
            "}";
    }
}
