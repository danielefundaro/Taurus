package com.fundaro.zodiac.taurus.domain.criteria;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fundaro.zodiac.taurus.domain.criteria.filter.DateFilter;
import org.springdoc.core.annotations.ParameterObject;
import tech.jhipster.service.filter.Filter;
import tech.jhipster.service.filter.StringFilter;

import java.util.Objects;
import java.util.Optional;

/**
 * Criteria class for the {@link com.fundaro.zodiac.taurus.domain.Albums} entity. This class is used
 * in {@link com.fundaro.zodiac.taurus.web.rest.AlbumsResource} to receive all the possible filtering options from
 * the Http GET request parameters.
 * For example the following could be a valid request:
 * {@code /albums?id.greaterThan=5&attr1.contains=something&attr2.specified=false}
 * As Spring is unable to properly convert the types, unless specific {@link Filter} class are used, we need to use
 * fix type specific filters.
 */
@ParameterObject
@SuppressWarnings("common-java:DuplicatedBlocks")
public class AlbumsCriteria extends CommonOpenSearchCriteria {

    private DateFilter date;

    private StringFilter trackName;

    @JsonIgnore
    private StringFilter trackId;

    public AlbumsCriteria() {
        super();
    }

    public AlbumsCriteria(AlbumsCriteria other) {
        super(other);
        this.date = other.optionalDate().map(DateFilter::copy).orElse(null);
        this.trackName = other.optionalTrackName().map(StringFilter::copy).orElse(null);
        this.trackId = other.optionalTrackId().map(StringFilter::copy).orElse(null);
    }

    @Override
    public AlbumsCriteria copy() {
        return new AlbumsCriteria(this);
    }

    public DateFilter getDate() {
        return date;
    }

    public Optional<DateFilter> optionalDate() {
        return Optional.ofNullable(date);
    }

    public AlbumsCriteria setDate(DateFilter date) {
        this.date = date;
        return this;
    }

    public StringFilter getTrackName() {
        return trackName;
    }

    public Optional<StringFilter> optionalTrackName() {
        return Optional.ofNullable(trackName);
    }

    public AlbumsCriteria setTrackName(StringFilter trackName) {
        this.trackName = trackName;
        return this;
    }

    public StringFilter getTrackId() {
        return trackId;
    }

    public Optional<StringFilter> optionalTrackId() {
        return Optional.ofNullable(trackId);
    }

    public AlbumsCriteria setTrackId(StringFilter trackId) {
        this.trackId = trackId;
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
        final AlbumsCriteria that = (AlbumsCriteria) o;
        return (
            super.equals(o) &&
                Objects.equals(date, that.date)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), date);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "AlbumsCriteria{" +
            optionalId().map(f -> "id=" + f + ", ").orElse("") +
            optionalName().map(f -> "name=" + f + ", ").orElse("") +
            optionalDescription().map(f -> "description=" + f + ", ").orElse("") +
            optionalDate().map(f -> "date=" + f + ", ").orElse("") +
            optionalTrackName().map(f -> "trackName=" + f + ", ").orElse("") +
            optionalTrackId().map(f -> "trackId=" + f + ", ").orElse("") +
            "}";
    }
}
