package com.fundaro.zodiac.taurus.domain.criteria;

import org.springdoc.core.annotations.ParameterObject;
import tech.jhipster.service.filter.Filter;
import tech.jhipster.service.filter.StringFilter;
import tech.jhipster.service.filter.ZonedDateTimeFilter;

import java.util.Objects;
import java.util.Optional;

/**
 * Criteria class for the {@link com.fundaro.zodiac.taurus.domain.Notices} entity. This class is used
 * in {@link com.fundaro.zodiac.taurus.web.rest.NoticesResource} to receive all the possible filtering options from
 * the Http GET request parameters.
 * For example the following could be a valid request:
 * {@code /notices?id.greaterThan=5&attr1.contains=something&attr2.specified=false}
 * As Spring is unable to properly convert the types, unless specific {@link Filter} class are used, we need to use
 * fix type specific filters.
 */
@ParameterObject
@SuppressWarnings("common-java:DuplicatedBlocks")
public class NoticesCriteria extends CommonCriteria {

    private StringFilter userId;

    private StringFilter name;

    private StringFilter message;

    private ZonedDateTimeFilter readDate;

    public NoticesCriteria() {
        super();
    }

    public NoticesCriteria(NoticesCriteria other) {
        super(other);
        this.userId = other.optionalUserId().map(StringFilter::copy).orElse(null);
        this.name = other.optionalName().map(StringFilter::copy).orElse(null);
        this.message = other.optionalMessage().map(StringFilter::copy).orElse(null);
        this.readDate = other.optionalReadDate().map(ZonedDateTimeFilter::copy).orElse(null);
    }

    @Override
    public NoticesCriteria copy() {
        return new NoticesCriteria(this);
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

    public StringFilter getMessage() {
        return message;
    }

    public Optional<StringFilter> optionalMessage() {
        return Optional.ofNullable(message);
    }

    public StringFilter message() {
        if (message == null) {
            setMessage(new StringFilter());
        }
        return message;
    }

    public void setMessage(StringFilter message) {
        this.message = message;
    }

    public ZonedDateTimeFilter getReadDate() {
        return readDate;
    }

    public Optional<ZonedDateTimeFilter> optionalReadDate() {
        return Optional.ofNullable(readDate);
    }

    public ZonedDateTimeFilter readDate() {
        if (readDate == null) {
            setReadDate(new ZonedDateTimeFilter());
        }
        return readDate;
    }

    public void setReadDate(ZonedDateTimeFilter readDate) {
        this.readDate = readDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final NoticesCriteria that = (NoticesCriteria) o;
        return (
            super.equals(o) &&
                Objects.equals(userId, that.userId) &&
                Objects.equals(name, that.name) &&
                Objects.equals(message, that.message) &&
                Objects.equals(readDate, that.readDate)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), userId, name, message, readDate);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "NoticesCriteria{" +
            optionalId().map(f -> "id=" + f + ", ").orElse("") +
            optionalUserId().map(f -> "userId=" + f + ", ").orElse("") +
            optionalName().map(f -> "name=" + f + ", ").orElse("") +
            optionalMessage().map(f -> "message=" + f + ", ").orElse("") +
            optionalReadDate().map(f -> "readDate=" + f + ", ").orElse("") +
            optionalDistinct().map(f -> "distinct=" + f + ", ").orElse("") +
            "}";
    }
}
