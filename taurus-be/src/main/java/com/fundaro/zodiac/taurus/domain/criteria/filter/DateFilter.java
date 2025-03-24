package com.fundaro.zodiac.taurus.domain.criteria.filter;

import org.springframework.format.annotation.DateTimeFormat;
import tech.jhipster.service.filter.RangeFilter;

import java.util.Date;
import java.util.List;

public class DateFilter extends RangeFilter<Date> {

    public DateFilter() {
    }

    public DateFilter(DateFilter filter) {
        super(filter);
    }

    public DateFilter copy() {
        return new DateFilter(this);
    }

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    public DateFilter setEquals(Date equals) {
        super.setEquals(equals);
        return this;
    }

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    public DateFilter setNotEquals(Date equals) {
        super.setNotEquals(equals);
        return this;
    }

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    public DateFilter setIn(List<Date> in) {
        super.setIn(in);
        return this;
    }

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    public DateFilter setNotIn(List<Date> notIn) {
        super.setNotIn(notIn);
        return this;
    }

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    public DateFilter setGreaterThan(Date equals) {
        super.setGreaterThan(equals);
        return this;
    }

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    public DateFilter setLessThan(Date equals) {
        super.setLessThan(equals);
        return this;
    }

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    public DateFilter setGreaterThanOrEqual(Date equals) {
        super.setGreaterThanOrEqual(equals);
        return this;
    }

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    public DateFilter setLessThanOrEqual(Date equals) {
        super.setLessThanOrEqual(equals);
        return this;
    }
}
