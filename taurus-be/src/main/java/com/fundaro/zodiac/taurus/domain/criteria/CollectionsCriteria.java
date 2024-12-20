package com.fundaro.zodiac.taurus.domain.criteria;

import org.springdoc.core.annotations.ParameterObject;
import tech.jhipster.service.filter.Filter;
import tech.jhipster.service.filter.LongFilter;

import java.util.Objects;
import java.util.Optional;

/**
 * Criteria class for the {@link com.fundaro.zodiac.taurus.domain.Collections} entity. This class is used
 * in {@link com.fundaro.zodiac.taurus.web.rest.CollectionsResource} to receive all the possible filtering options from
 * the Http GET request parameters.
 * For example the following could be a valid request:
 * {@code /collections?id.greaterThan=5&attr1.contains=something&attr2.specified=false}
 * As Spring is unable to properly convert the types, unless specific {@link Filter} class are used, we need to use
 * fix type specific filters.
 */
@ParameterObject
@SuppressWarnings("common-java:DuplicatedBlocks")
public class CollectionsCriteria extends CommonCriteria {

    private LongFilter orderNumber;

    private LongFilter albumId;

    private LongFilter trackId;

    public CollectionsCriteria() {
        super();
    }

    public CollectionsCriteria(CollectionsCriteria other) {
        super(other);
        this.orderNumber = other.optionalOrderNumber().map(LongFilter::copy).orElse(null);
        this.albumId = other.optionalAlbumId().map(LongFilter::copy).orElse(null);
        this.trackId = other.optionalTrackId().map(LongFilter::copy).orElse(null);
    }

    @Override
    public CollectionsCriteria copy() {
        return new CollectionsCriteria(this);
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

    public LongFilter getAlbumId() {
        return albumId;
    }

    public Optional<LongFilter> optionalAlbumId() {
        return Optional.ofNullable(albumId);
    }

    public LongFilter albumId() {
        if (albumId == null) {
            setAlbumId(new LongFilter());
        }
        return albumId;
    }

    public void setAlbumId(LongFilter albumId) {
        this.albumId = albumId;
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
        final CollectionsCriteria that = (CollectionsCriteria) o;
        return (
            super.equals(o) &&
                Objects.equals(orderNumber, that.orderNumber) &&
                Objects.equals(albumId, that.albumId) &&
                Objects.equals(trackId, that.trackId)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), orderNumber, albumId, trackId);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "CollectionsCriteria{" +
            optionalId().map(f -> "id=" + f + ", ").orElse("") +
            optionalOrderNumber().map(f -> "orderNumber=" + f + ", ").orElse("") +
            optionalAlbumId().map(f -> "albumId=" + f + ", ").orElse("") +
            optionalTrackId().map(f -> "trackId=" + f + ", ").orElse("") +
            optionalDistinct().map(f -> "distinct=" + f + ", ").orElse("") +
            "}";
    }
}
