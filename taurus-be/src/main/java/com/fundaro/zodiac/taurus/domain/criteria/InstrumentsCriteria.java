package com.fundaro.zodiac.taurus.domain.criteria;

import org.springdoc.core.annotations.ParameterObject;
import tech.jhipster.service.filter.Filter;

import java.util.Objects;

/**
 * Criteria class for the {@link com.fundaro.zodiac.taurus.domain.Instruments} entity. This class is used
 * in {@link com.fundaro.zodiac.taurus.web.rest.InstrumentsResource} to receive all the possible filtering options from
 * the Http GET request parameters.
 * For example the following could be a valid request:
 * {@code /instruments?id.greaterThan=5&attr1.contains=something&attr2.specified=false}
 * As Spring is unable to properly convert the types, unless specific {@link Filter} class are used, we need to use
 * fix type specific filters.
 */
@ParameterObject
@SuppressWarnings("common-java:DuplicatedBlocks")
public class InstrumentsCriteria extends CommonOpenSearchCriteria {

    public InstrumentsCriteria() {
    }

    public InstrumentsCriteria(InstrumentsCriteria other) {
        super(other);
    }

    @Override
    public InstrumentsCriteria copy() {
        return new InstrumentsCriteria(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final InstrumentsCriteria that = (InstrumentsCriteria) o;
        return (
            super.equals(o)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode());
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "InstrumentsCriteria{" +
            optionalId().map(f -> "id=" + f + ", ").orElse("") +
            optionalName().map(f -> "name=" + f + ", ").orElse("") +
            optionalDescription().map(f -> "description=" + f + ", ").orElse("") +
            "}";
    }
}
