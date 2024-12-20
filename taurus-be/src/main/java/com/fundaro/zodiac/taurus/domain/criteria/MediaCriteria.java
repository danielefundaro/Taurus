package com.fundaro.zodiac.taurus.domain.criteria;

import org.springdoc.core.annotations.ParameterObject;
import tech.jhipster.service.filter.Filter;
import tech.jhipster.service.filter.LongFilter;
import tech.jhipster.service.filter.StringFilter;

import java.util.Objects;
import java.util.Optional;

/**
 * Criteria class for the {@link com.fundaro.zodiac.taurus.domain.Media} entity. This class is used
 * in {@link com.fundaro.zodiac.taurus.web.rest.MediaResource} to receive all the possible filtering options from
 * the Http GET request parameters.
 * For example the following could be a valid request:
 * {@code /media?id.greaterThan=5&attr1.contains=something&attr2.specified=false}
 * As Spring is unable to properly convert the types, unless specific {@link Filter} class are used, we need to use
 * fix type specific filters.
 */
@ParameterObject
@SuppressWarnings("common-java:DuplicatedBlocks")
public class MediaCriteria extends CommonCriteria {

    private StringFilter name;

    private StringFilter description;

    private LongFilter orderNumber;

    private LongFilter instrumentId;

    private LongFilter trackId;

    public MediaCriteria() {
        super();
    }

    public MediaCriteria(MediaCriteria other) {
        super(other);
        this.name = other.optionalName().map(StringFilter::copy).orElse(null);
        this.description = other.optionalDescription().map(StringFilter::copy).orElse(null);
        this.orderNumber = other.optionalOrderNumber().map(LongFilter::copy).orElse(null);
        this.instrumentId = other.optionalInstrumentId().map(LongFilter::copy).orElse(null);
        this.trackId = other.optionalTrackId().map(LongFilter::copy).orElse(null);
    }

    @Override
    public MediaCriteria copy() {
        return new MediaCriteria(this);
    }

    public StringFilter getName() {
        return name;
    }

    public Optional<StringFilter> optionalName() {
        return Optional.ofNullable(name);
    }

    public StringFilter name() {
        if (name == null) {
            setName(new StringFilter());
        }
        return name;
    }

    public void setName(StringFilter name) {
        this.name = name;
    }

    public StringFilter getDescription() {
        return description;
    }

    public Optional<StringFilter> optionalDescription() {
        return Optional.ofNullable(description);
    }

    public StringFilter description() {
        if (description == null) {
            setDescription(new StringFilter());
        }
        return description;
    }

    public void setDescription(StringFilter description) {
        this.description = description;
    }

    public LongFilter getOrderNumber() {
        return orderNumber;
    }

    public Optional<LongFilter> optionalOrderNumber() {
        return Optional.ofNullable(orderNumber);
    }

    public LongFilter orderNumber() {
        if (orderNumber == null) {
            setOrderNumber(new LongFilter());
        }
        return orderNumber;
    }

    public void setOrderNumber(LongFilter orderNumber) {
        this.orderNumber = orderNumber;
    }

    public LongFilter getInstrumentId() {
        return instrumentId;
    }

    public Optional<LongFilter> optionalInstrumentId() {
        return Optional.ofNullable(instrumentId);
    }

    public LongFilter instrumentId() {
        if (instrumentId == null) {
            setInstrumentId(new LongFilter());
        }
        return instrumentId;
    }

    public void setInstrumentId(LongFilter instrumentId) {
        this.instrumentId = instrumentId;
    }

    public LongFilter getTrackId() {
        return trackId;
    }

    public Optional<LongFilter> optionalTrackId() {
        return Optional.ofNullable(trackId);
    }

    public LongFilter trackId() {
        if (trackId == null) {
            setTrackId(new LongFilter());
        }
        return trackId;
    }

    public void setTrackId(LongFilter trackId) {
        this.trackId = trackId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final MediaCriteria that = (MediaCriteria) o;
        return (
            super.equals(o) &&
                Objects.equals(name, that.name) &&
                Objects.equals(description, that.description) &&
                Objects.equals(orderNumber, that.orderNumber) &&
                Objects.equals(instrumentId, that.instrumentId) &&
                Objects.equals(trackId, that.trackId)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), name, description, orderNumber, instrumentId, trackId);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "MediaCriteria{" +
            optionalId().map(f -> "id=" + f + ", ").orElse("") +
            optionalName().map(f -> "name=" + f + ", ").orElse("") +
            optionalDescription().map(f -> "description=" + f + ", ").orElse("") +
            optionalOrderNumber().map(f -> "orderNumber=" + f + ", ").orElse("") +
            optionalInstrumentId().map(f -> "instrumentId=" + f + ", ").orElse("") +
            optionalTrackId().map(f -> "trackId=" + f + ", ").orElse("") +
            optionalDistinct().map(f -> "distinct=" + f + ", ").orElse("") +
            "}";
    }
}
