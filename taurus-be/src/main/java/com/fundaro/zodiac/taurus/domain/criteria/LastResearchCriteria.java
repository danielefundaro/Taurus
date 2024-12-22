package com.fundaro.zodiac.taurus.domain.criteria;

import org.springdoc.core.annotations.ParameterObject;
import tech.jhipster.service.filter.Filter;
import tech.jhipster.service.filter.StringFilter;

import java.util.Objects;
import java.util.Optional;

/**
 * Criteria class for the {@link com.fundaro.zodiac.taurus.domain.LastResearch} entity. This class is used
 * in {@link com.fundaro.zodiac.taurus.web.rest.LastResearchResource} to receive all the possible filtering options from
 * the Http GET request parameters.
 * For example the following could be a valid request:
 * {@code /last-researches?id.greaterThan=5&attr1.contains=something&attr2.specified=false}
 * As Spring is unable to properly convert the types, unless specific {@link Filter} class are used, we need to use
 * fix type specific filters.
 */
@ParameterObject
@SuppressWarnings("common-java:DuplicatedBlocks")
public class LastResearchCriteria extends CommonCriteria {

    private StringFilter userId;

    private StringFilter value;

    private StringFilter field;

    public LastResearchCriteria() {
        super();
    }

    public LastResearchCriteria(LastResearchCriteria other) {
        super(other);
        this.userId = other.optionalUserId().map(StringFilter::copy).orElse(null);
        this.value = other.optionalValue().map(StringFilter::copy).orElse(null);
        this.field = other.optionalField().map(StringFilter::copy).orElse(null);
    }

    @Override
    public LastResearchCriteria copy() {
        return new LastResearchCriteria(this);
    }

    public StringFilter getUserId() {
        return userId;
    }

    public Optional<StringFilter> optionalUserId() {
        return Optional.ofNullable(userId);
    }

    public StringFilter userId() {
        if (userId == null) {
            setUserId(new StringFilter());
        }
        return userId;
    }

    public void setUserId(StringFilter userId) {
        this.userId = userId;
    }

    public StringFilter getValue() {
        return value;
    }

    public Optional<StringFilter> optionalValue() {
        return Optional.ofNullable(value);
    }

    public StringFilter value() {
        if (value == null) {
            setValue(new StringFilter());
        }
        return value;
    }

    public void setValue(StringFilter value) {
        this.value = value;
    }

    public StringFilter getField() {
        return field;
    }

    public Optional<StringFilter> optionalField() {
        return Optional.ofNullable(field);
    }

    public StringFilter field() {
        if (field == null) {
            setField(new StringFilter());
        }
        return field;
    }

    public void setField(StringFilter field) {
        this.field = field;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final LastResearchCriteria that = (LastResearchCriteria) o;
        return (
            super.equals(o) &&
                Objects.equals(userId, that.userId) &&
                Objects.equals(value, that.value) &&
                Objects.equals(field, that.field)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), userId, value, field);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "LastResearchCriteria{" +
            optionalId().map(f -> "id=" + f + ", ").orElse("") +
            optionalUserId().map(f -> "userId=" + f + ", ").orElse("") +
            optionalValue().map(f -> "value=" + f + ", ").orElse("") +
            optionalField().map(f -> "field=" + f + ", ").orElse("") +
            optionalDistinct().map(f -> "distinct=" + f + ", ").orElse("") +
            "}";
    }
}
