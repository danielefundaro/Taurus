package com.fundaro.zodiac.taurus.domain.criteria;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springdoc.core.annotations.ParameterObject;
import tech.jhipster.service.filter.Filter;
import tech.jhipster.service.filter.StringFilter;

import java.util.Objects;
import java.util.Optional;

/**
 * Criteria class for the {@link com.fundaro.zodiac.taurus.domain.Tracks} entity. This class is used
 * in {@link com.fundaro.zodiac.taurus.web.rest.TracksResource} to receive all the possible filtering options from
 * the Http GET request parameters.
 * For example the following could be a valid request:
 * {@code /tracks?id.greaterThan=5&attr1.contains=something&attr2.specified=false}
 * As Spring is unable to properly convert the types, unless specific {@link Filter} class are used, we need to use
 * fix type specific filters.
 */
@ParameterObject
@SuppressWarnings("common-java:DuplicatedBlocks")
public class TracksCriteria extends CommonOpenSearchCriteria {

    private StringFilter composer;

    private StringFilter arranger;

    private StringFilter type;

    private StringFilter instrumentId;

    @JsonIgnore
    private StringFilter mediaId;

    public TracksCriteria() {
        super();
    }

    public TracksCriteria(TracksCriteria other) {
        super(other);
        this.composer = other.optionalComposer().map(StringFilter::copy).orElse(null);
        this.arranger = other.optionalArranger().map(StringFilter::copy).orElse(null);
        this.type = other.optionalTypeId().map(StringFilter::copy).orElse(null);
        this.instrumentId = other.optionalInstrumentId().map(StringFilter::copy).orElse(null);
        this.mediaId = other.optionalMediaId().map(StringFilter::copy).orElse(null);
    }

    @Override
    public TracksCriteria copy() {
        return new TracksCriteria(this);
    }

    public StringFilter getComposer() {
        return composer;
    }

    public Optional<StringFilter> optionalComposer() {
        return Optional.ofNullable(composer);
    }

    public TracksCriteria setComposer(StringFilter composer) {
        this.composer = composer;
        return this;
    }

    public StringFilter getArranger() {
        return arranger;
    }

    public Optional<StringFilter> optionalArranger() {
        return Optional.ofNullable(arranger);
    }

    public TracksCriteria setArranger(StringFilter arranger) {
        this.arranger = arranger;
        return this;
    }

    public StringFilter getType() {
        return type;
    }

    public Optional<StringFilter> optionalTypeId() {
        return Optional.ofNullable(type);
    }

    public TracksCriteria setType(StringFilter type) {
        this.type = type;
        return this;
    }

    public StringFilter getInstrumentId() {
        return instrumentId;
    }

    public Optional<StringFilter> optionalInstrumentId() {
        return Optional.ofNullable(instrumentId);
    }

    public TracksCriteria setInstrumentId(StringFilter instrumentId) {
        this.instrumentId = instrumentId;
        return this;
    }

    public StringFilter getMediaId() {
        return mediaId;
    }

    public Optional<StringFilter> optionalMediaId() {
        return Optional.ofNullable(mediaId);
    }

    public TracksCriteria setMediaId(StringFilter mediaId) {
        this.mediaId = mediaId;
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
        final TracksCriteria that = (TracksCriteria) o;
        return (
            super.equals(o) &&
                Objects.equals(composer, that.composer) &&
                Objects.equals(arranger, that.arranger) &&
                Objects.equals(type, that.type) &&
                Objects.equals(instrumentId, that.instrumentId)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), composer, arranger, type, instrumentId);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "TracksCriteria{" +
            optionalId().map(f -> "id=" + f + ", ").orElse("") +
            optionalName().map(f -> "name=" + f + ", ").orElse("") +
            optionalDescription().map(f -> "description=" + f + ", ").orElse("") +
            optionalComposer().map(f -> "composer=" + f + ", ").orElse("") +
            optionalArranger().map(f -> "arranger=" + f + ", ").orElse("") +
            optionalTypeId().map(f -> "type=" + f + ", ").orElse("") +
            optionalInstrumentId().map(f -> "instrumentId=" + f + ", ").orElse("") +
            "}";
    }
}
