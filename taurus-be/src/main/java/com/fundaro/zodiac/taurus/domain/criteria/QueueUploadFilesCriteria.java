package com.fundaro.zodiac.taurus.domain.criteria;

import com.fundaro.zodiac.taurus.domain.enumeration.UploadFileStatus;
import org.springdoc.core.annotations.ParameterObject;
import tech.jhipster.service.filter.Filter;
import tech.jhipster.service.filter.StringFilter;

import java.util.Objects;
import java.util.Optional;

/**
 * Criteria class for the {@link com.fundaro.zodiac.taurus.domain.QueueUploadFiles} entity. This class is used
 * in {@link com.fundaro.zodiac.taurus.web.rest.LastResearchResource} to receive all the possible filtering options from
 * the Http GET request parameters.
 * For example the following could be a valid request:
 * {@code /queue-upload-files?id.greaterThan=5&attr1.contains=something&attr2.specified=false}
 * As Spring is unable to properly convert the types, unless specific {@link Filter} class are used, we need to use
 * fix type specific filters.
 */
@ParameterObject
@SuppressWarnings("common-java:DuplicatedBlocks")
public class QueueUploadFilesCriteria extends CommonCriteria {

    public static class StatusFilter extends Filter<UploadFileStatus> {

        public StatusFilter() {
        }

        public StatusFilter(StatusFilter filter) {
            super(filter);
        }

        @Override
        public StatusFilter copy() {
            return new StatusFilter(this);
        }
    }

    private StringFilter userId;

    private StringFilter trackId;

    private StatusFilter status;

    public QueueUploadFilesCriteria() {
        super();
    }

    public QueueUploadFilesCriteria(QueueUploadFilesCriteria other) {
        super(other);
        this.userId = other.optionalUserId().map(StringFilter::copy).orElse(null);
        this.trackId = other.optionalTrackId().map(StringFilter::copy).orElse(null);
        this.status = other.optionalStatus().map(StatusFilter::copy).orElse(null);
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

    public StringFilter userId() {
        if (userId == null) {
            setUserId(new StringFilter());
        }
        return userId;
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

    public StringFilter trackId() {
        if (trackId == null) {
            setTrackId(new StringFilter());
        }
        return trackId;
    }

    public void setTrackId(StringFilter trackId) {
        this.trackId = trackId;
    }

    public StatusFilter getStatus() {
        return status;
    }

    public Optional<StatusFilter> optionalStatus() {
        return Optional.ofNullable(status);
    }

    public StatusFilter status() {
        if (status == null) {
            setStatus(new StatusFilter());
        }
        return status;
    }

    public void setStatus(StatusFilter status) {
        this.status = status;
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
                Objects.equals(status, that.status)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), userId, trackId, status);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "LastResearchCriteria{" +
            optionalId().map(f -> "id=" + f + ", ").orElse("") +
            optionalUserId().map(f -> "userId=" + f + ", ").orElse("") +
            optionalTrackId().map(f -> "trackId=" + f + ", ").orElse("") +
            optionalStatus().map(f -> "status=" + f + ", ").orElse("") +
            optionalDistinct().map(f -> "distinct=" + f + ", ").orElse("") +
            "}";
    }
}
