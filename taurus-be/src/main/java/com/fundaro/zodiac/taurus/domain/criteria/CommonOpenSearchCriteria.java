package com.fundaro.zodiac.taurus.domain.criteria;

import tech.jhipster.service.Criteria;
import tech.jhipster.service.filter.StringFilter;

import java.io.Serializable;
import java.util.Objects;
import java.util.Optional;

public class CommonOpenSearchCriteria implements Serializable, Criteria {

    private StringFilter id;

    private StringFilter name;

    private StringFilter description;

    public CommonOpenSearchCriteria() {
    }

    public CommonOpenSearchCriteria(CommonOpenSearchCriteria other) {
        this.id = other.optionalId().map(StringFilter::copy).orElse(null);
        this.name = other.optionalId().map(StringFilter::copy).orElse(null);
        this.description = other.optionalId().map(StringFilter::copy).orElse(null);
    }

    @Override
    public CommonOpenSearchCriteria copy() {
        return new CommonOpenSearchCriteria(this);
    }

    public StringFilter getId() {
        return id;
    }

    public Optional<StringFilter> optionalId() {
        return Optional.ofNullable(id);
    }

    public StringFilter id() {
        if (id == null) {
            setId(new StringFilter());
        }
        return id;
    }

    public void setId(StringFilter name) {
        this.name = name;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        final CommonOpenSearchCriteria that = (CommonOpenSearchCriteria) o;
        return Objects.equals(id, that.id) &&
            Objects.equals(name, that.name) &&
            Objects.equals(description, that.description);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, description);
    }
}
