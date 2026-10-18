package com.fundaro.zodiac.taurus.domain.criteria;

import org.springdoc.core.annotations.ParameterObject;
import tech.jhipster.service.filter.BooleanFilter;
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

    /** Instruments with ({@code true}) or without ({@code false}) their playback settings. */
    private BooleanFilter playbackConfigured;

    public InstrumentsCriteria() {
    }

    public InstrumentsCriteria(InstrumentsCriteria other) {
        super(other);
        this.playbackConfigured = other.playbackConfigured == null ? null : other.playbackConfigured.copy();
    }

    @Override
    public InstrumentsCriteria copy() {
        return new InstrumentsCriteria(this);
    }

    public BooleanFilter getPlaybackConfigured() {
        return playbackConfigured;
    }

    public void setPlaybackConfigured(BooleanFilter playbackConfigured) {
        this.playbackConfigured = playbackConfigured;
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
            super.equals(o) &&
            Objects.equals(playbackConfigured, that.playbackConfigured)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), playbackConfigured);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "InstrumentsCriteria{" +
            optionalId().map(f -> "id=" + f + ", ").orElse("") +
            optionalName().map(f -> "name=" + f + ", ").orElse("") +
            optionalDescription().map(f -> "description=" + f + ", ").orElse("") +
            (playbackConfigured != null ? "playbackConfigured=" + playbackConfigured + ", " : "") +
            "}";
    }
}
