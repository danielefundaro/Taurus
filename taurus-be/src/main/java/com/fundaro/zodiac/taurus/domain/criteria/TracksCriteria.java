package com.fundaro.zodiac.taurus.domain.criteria;

import org.springdoc.core.annotations.ParameterObject;
import tech.jhipster.service.filter.Filter;
import tech.jhipster.service.filter.LongFilter;
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
public class TracksCriteria extends CommonCriteria {

    private StringFilter name;

    private StringFilter description;

    private StringFilter composer;

    private StringFilter arranger;

    private LongFilter typeId;

    public TracksCriteria() {
        super();
    }

    public TracksCriteria(TracksCriteria other) {
        super(other);
        this.name = other.optionalName().map(StringFilter::copy).orElse(null);
        this.description = other.optionalDescription().map(StringFilter::copy).orElse(null);
        this.composer = other.optionalComposer().map(StringFilter::copy).orElse(null);
        this.arranger = other.optionalArranger().map(StringFilter::copy).orElse(null);
        this.typeId = other.optionalTypeId().map(LongFilter::copy).orElse(null);
    }

    @Override
    public TracksCriteria copy() {
        return new TracksCriteria(this);
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

    public StringFilter getComposer() {
        return composer;
    }

    public Optional<StringFilter> optionalComposer() {
        return Optional.ofNullable(composer);
    }

    public StringFilter composer() {
        if (composer == null) {
            setComposer(new StringFilter());
        }
        return composer;
    }

    public void setComposer(StringFilter composer) {
        this.composer = composer;
    }

    public StringFilter getArranger() {
        return arranger;
    }

    public Optional<StringFilter> optionalArranger() {
        return Optional.ofNullable(arranger);
    }

    public StringFilter arranger() {
        if (arranger == null) {
            setArranger(new StringFilter());
        }
        return arranger;
    }

    public void setArranger(StringFilter arranger) {
        this.arranger = arranger;
    }

    public LongFilter getTypeId() {
        return typeId;
    }

    public Optional<LongFilter> optionalTypeId() {
        return Optional.ofNullable(typeId);
    }

    public LongFilter typeId() {
        if (typeId == null) {
            setTypeId(new LongFilter());
        }
        return typeId;
    }

    public void setTypeId(LongFilter typeId) {
        this.typeId = typeId;
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
                Objects.equals(name, that.name) &&
                Objects.equals(description, that.description) &&
                Objects.equals(composer, that.composer) &&
                Objects.equals(arranger, that.arranger) &&
                Objects.equals(typeId, that.typeId)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), name, description, composer, arranger, typeId);
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
            optionalTypeId().map(f -> "typeId=" + f + ", ").orElse("") +
            optionalDistinct().map(f -> "distinct=" + f + ", ").orElse("") +
            "}";
    }
}
