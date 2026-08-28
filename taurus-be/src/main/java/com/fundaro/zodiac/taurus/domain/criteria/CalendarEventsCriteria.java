package com.fundaro.zodiac.taurus.domain.criteria;

import com.fundaro.zodiac.taurus.domain.criteria.filter.DateFilter;
import com.fundaro.zodiac.taurus.domain.criteria.filter.StateFilter;
import org.springdoc.core.annotations.ParameterObject;
import tech.jhipster.service.filter.StringFilter;
import tech.jhipster.service.filter.LongFilter;

import java.util.Objects;
import java.util.Optional;

@ParameterObject
@SuppressWarnings("common-java:DuplicatedBlocks")
public class CalendarEventsCriteria extends CommonOpenSearchCriteria {

    private DateFilter startDate;
    private DateFilter endDate;
    private StringFilter location;
    private StateFilter state;
    private LongFilter presentUserId;

    public CalendarEventsCriteria() {
        super();
    }

    public CalendarEventsCriteria(CalendarEventsCriteria other) {
        super(other);
        this.startDate = other.optionalStartDate().map(DateFilter::copy).orElse(null);
        this.endDate = other.optionalEndDate().map(DateFilter::copy).orElse(null);
        this.location = other.optionalLocation().map(StringFilter::copy).orElse(null);
        this.state = other.optionalState().map(StateFilter::copy).orElse(null);
        this.presentUserId = other.optionalPresentUserId().map(LongFilter::copy).orElse(null);
    }

    @Override
    public CalendarEventsCriteria copy() {
        return new CalendarEventsCriteria(this);
    }

    public DateFilter getStartDate() {
        return startDate;
    }

    public Optional<DateFilter> optionalStartDate() {
        return Optional.ofNullable(startDate);
    }

    public CalendarEventsCriteria setStartDate(DateFilter startDate) {
        this.startDate = startDate;
        return this;
    }

    public DateFilter getEndDate() {
        return endDate;
    }

    public Optional<DateFilter> optionalEndDate() {
        return Optional.ofNullable(endDate);
    }

    public CalendarEventsCriteria setEndDate(DateFilter endDate) {
        this.endDate = endDate;
        return this;
    }

    public StringFilter getLocation() {
        return location;
    }

    public Optional<StringFilter> optionalLocation() {
        return Optional.ofNullable(location);
    }

    public CalendarEventsCriteria setLocation(StringFilter location) {
        this.location = location;
        return this;
    }

    public StateFilter getState() {
        return state;
    }

    public Optional<StateFilter> optionalState() {
        return Optional.ofNullable(state);
    }

    public CalendarEventsCriteria setState(StateFilter state) {
        this.state = state;
        return this;
    }

    public LongFilter getPresentUserId() {
        return presentUserId;
    }

    public Optional<LongFilter> optionalPresentUserId() {
        return Optional.ofNullable(presentUserId);
    }

    public CalendarEventsCriteria setPresentUserId(LongFilter presentUserId) {
        this.presentUserId = presentUserId;
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final CalendarEventsCriteria that = (CalendarEventsCriteria) o;
        return super.equals(o) &&
            Objects.equals(startDate, that.startDate) &&
            Objects.equals(endDate, that.endDate) &&
            Objects.equals(location, that.location) &&
            Objects.equals(state, that.state) &&
            Objects.equals(presentUserId, that.presentUserId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), startDate, endDate, location, state, presentUserId);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "CalendarEventsCriteria{" +
            optionalId().map(f -> "id=" + f + ", ").orElse("") +
            optionalName().map(f -> "name=" + f + ", ").orElse("") +
            optionalStartDate().map(f -> "startDate=" + f + ", ").orElse("") +
            optionalEndDate().map(f -> "endDate=" + f + ", ").orElse("") +
            optionalLocation().map(f -> "location=" + f + ", ").orElse("") +
            optionalState().map(f -> "state=" + f + ", ").orElse("") +
            optionalPresentUserId().map(f -> "presentUserId=" + f + ", ").orElse("") +
            "}";
    }
}
