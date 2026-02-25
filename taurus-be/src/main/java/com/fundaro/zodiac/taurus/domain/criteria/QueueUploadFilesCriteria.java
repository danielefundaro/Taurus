package com.fundaro.zodiac.taurus.domain.criteria;

import com.fundaro.zodiac.taurus.domain.criteria.filter.UploadFileStatusFilter;
import org.springdoc.core.annotations.ParameterObject;
import tech.jhipster.service.filter.Filter;
import tech.jhipster.service.filter.StringFilter;

import java.util.Objects;
import java.util.Optional;

/**
 * Criteria class for the {@link com.fundaro.zodiac.taurus.domain.QueueUploadFiles} entity
 * As Spring is unable to properly convert the types, unless specific {@link Filter} class are used, we need to use
 * fix type specific filters.
 */
@ParameterObject
@SuppressWarnings("common-java:DuplicatedBlocks")
public class QueueUploadFilesCriteria extends CommonOpenSearchCriteria {

    private StringFilter userId;

    private StringFilter trackId;

    private UploadFileStatusFilter status;

    private StringFilter type;

    public QueueUploadFilesCriteria() {
        super();
    }

    public QueueUploadFilesCriteria(QueueUploadFilesCriteria other) {
        super(other);
        this.userId = other.optionalUserId().map(StringFilter::copy).orElse(null);
        this.trackId = other.optionalTrackId().map(StringFilter::copy).orElse(null);
        this.status = other.optionalStatus().map(UploadFileStatusFilter::copy).orElse(null);
        this.type = other.optionalType().map(StringFilter::copy).orElse(null);
    }

    @Override
    public QueueUploadFilesCriteria copy() {
        return new QueueUploadFilesCriteria(this);
    }

    public StringFilter getUserId() {
        return userId;
    }

    public Optional<StringFilter> optionalUserId() {
        return Optional.ofNullable(userId);
    }

    public void setUserId(StringFilter userId) {
        this.userId = userId;
    }

    public StringFilter getTrackId() {
        return trackId;
    }

    public Optional<StringFilter> optionalTrackId() {
        return Optional.ofNullable(trackId);
    }

    public void setTrackId(StringFilter trackId) {
        this.trackId = trackId;
    }

    public UploadFileStatusFilter getStatus() {
        return status;
    }

    public Optional<UploadFileStatusFilter> optionalStatus() {
        return Optional.ofNullable(status);
    }

    public void setStatus(UploadFileStatusFilter status) {
        this.status = status;
    }

    public StringFilter getType() {
        return type;
    }

    public Optional<StringFilter> optionalType() {
        return Optional.ofNullable(type);
    }

    public void setType(StringFilter type) {
        this.type = type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final QueueUploadFilesCriteria that = (QueueUploadFilesCriteria) o;
        return (
            super.equals(o) &&
                Objects.equals(userId, that.userId) &&
                Objects.equals(trackId, that.trackId) &&
                Objects.equals(status, that.status) &&
                Objects.equals(type, that.type)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), userId, trackId, status, type);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "LastResearchCriteria{" +
            optionalId().map(f -> "id=" + f + ", ").orElse("") +
            optionalUserId().map(f -> "userId=" + f + ", ").orElse("") +
            optionalTrackId().map(f -> "trackId=" + f + ", ").orElse("") +
            optionalStatus().map(f -> "status=" + f + ", ").orElse("") +
            optionalType().map(f -> "type=" + f + ", ").orElse("") +
            "}";
    }
}
