package com.fundaro.zodiac.taurus.domain.criteria;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;
import java.util.Objects;
import java.util.Optional;

@ParameterObject
@SuppressWarnings("common-java:DuplicatedBlocks")
public class UserCalendarEventsCriteria {

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Date startDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Date endDate;

    public UserCalendarEventsCriteria() {
        super();
    }

    public UserCalendarEventsCriteria(UserCalendarEventsCriteria other) {
        this.startDate = other.getStartDate();
        this.endDate = other.getEndDate();
    }

    public UserCalendarEventsCriteria copy() {
        return new UserCalendarEventsCriteria(this);
    }

    public Date getStartDate() {
        return startDate;
    }

    public Optional<Date> optionalStartDate() {
        return Optional.ofNullable(startDate);
    }

    public UserCalendarEventsCriteria setStartDate(Date startDate) {
        this.startDate = startDate;
        return this;
    }

    public Date getEndDate() {
        return endDate;
    }

    public Optional<Date> optionalEndDate() {
        return Optional.ofNullable(endDate);
    }

    public UserCalendarEventsCriteria setEndDate(Date endDate) {
        this.endDate = endDate;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final UserCalendarEventsCriteria that = (UserCalendarEventsCriteria) o;
        return super.equals(o) &&
            Objects.equals(startDate, that.startDate) &&
            Objects.equals(endDate, that.endDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), startDate, endDate);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "UserCalendarEventsCriteria{" +
            optionalStartDate().map(f -> "startDate=" + f + ", ").orElse("") +
            optionalEndDate().map(f -> "endDate=" + f + ", ").orElse("") +
            "}";
    }
}
