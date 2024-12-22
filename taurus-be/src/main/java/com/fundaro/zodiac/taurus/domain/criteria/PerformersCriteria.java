package com.fundaro.zodiac.taurus.domain.criteria;

import org.springdoc.core.annotations.ParameterObject;
import tech.jhipster.service.filter.Filter;
import tech.jhipster.service.filter.LongFilter;
import tech.jhipster.service.filter.StringFilter;

import java.util.Objects;
import java.util.Optional;

/**
 * Criteria class for the {@link com.fundaro.zodiac.taurus.domain.Performers} entity. This class is used
 * in {@link com.fundaro.zodiac.taurus.web.rest.PerformersResource} to receive all the possible filtering options from
 * the Http GET request parameters.
 * For example the following could be a valid request:
 * {@code /performers?id.greaterThan=5&attr1.contains=something&attr2.specified=false}
 * As Spring is unable to properly convert the types, unless specific {@link Filter} class are used, we need to use
 * fix type specific filters.
 */
@ParameterObject
@SuppressWarnings("common-java:DuplicatedBlocks")
public class PerformersCriteria extends CommonCriteria {

    private StringFilter description;

    private LongFilter instrumentId;

    private LongFilter mediaId;

    public PerformersCriteria() {
        super();
    }

    public PerformersCriteria(PerformersCriteria other) {
        super(other);
        this.description = other.optionalDescription().map(StringFilter::copy).orElse(null);
        this.instrumentId = other.optionalInstrumentId().map(LongFilter::copy).orElse(null);
        this.mediaId = other.optionalMediaId().map(LongFilter::copy).orElse(null);
    }

    @Override
    public PerformersCriteria copy() {
        return new PerformersCriteria(this);
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

    public LongFilter getMediaId() {
        return mediaId;
    }

    public Optional<LongFilter> optionalMediaId() {
        return Optional.ofNullable(mediaId);
    }

    public LongFilter mediaId() {
        if (mediaId == null) {
            setMediaId(new LongFilter());
        }
        return mediaId;
    }

    public void setMediaId(LongFilter mediaId) {
        this.mediaId = mediaId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PerformersCriteria that = (PerformersCriteria) o;
        return (
            super.equals(o) &&
                Objects.equals(description, that.description) &&
                Objects.equals(instrumentId, that.instrumentId) &&
                Objects.equals(mediaId, that.mediaId)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), description, instrumentId, mediaId);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "PerformersCriteria{" +
            optionalId().map(f -> "id=" + f + ", ").orElse("") +
            optionalDescription().map(f -> "description=" + f + ", ").orElse("") +
            optionalInstrumentId().map(f -> "instrumentId=" + f + ", ").orElse("") +
            optionalMediaId().map(f -> "mediaId=" + f + ", ").orElse("") +
            optionalDistinct().map(f -> "distinct=" + f + ", ").orElse("") +
            "}";
    }
}
