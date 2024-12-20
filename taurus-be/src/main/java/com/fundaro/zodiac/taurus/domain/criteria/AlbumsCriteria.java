package com.fundaro.zodiac.taurus.domain.criteria;

import org.springdoc.core.annotations.ParameterObject;
import tech.jhipster.service.filter.Filter;
import tech.jhipster.service.filter.StringFilter;
import tech.jhipster.service.filter.ZonedDateTimeFilter;

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
public class AlbumsCriteria extends CommonCriteria {

    private StringFilter name;

    private StringFilter description;

    private ZonedDateTimeFilter date;

    public AlbumsCriteria() {
        super();
    }

    public AlbumsCriteria(AlbumsCriteria other) {
        super(other);
        this.name = other.optionalName().map(StringFilter::copy).orElse(null);
        this.description = other.optionalDescription().map(StringFilter::copy).orElse(null);
        this.date = other.optionalDate().map(ZonedDateTimeFilter::copy).orElse(null);
    }

    @Override
    public AlbumsCriteria copy() {
        return new AlbumsCriteria(this);
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

    public ZonedDateTimeFilter getDate() {
        return date;
    }

    public Optional<ZonedDateTimeFilter> optionalDate() {
        return Optional.ofNullable(date);
    }

    public ZonedDateTimeFilter date() {
        if (date == null) {
            setDate(new ZonedDateTimeFilter());
        }
        return date;
    }

    public void setDate(ZonedDateTimeFilter date) {
        this.date = date;
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
                Objects.equals(name, that.name) &&
                Objects.equals(description, that.description) &&
                Objects.equals(date, that.date)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), name, description, date);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "AlbumsCriteria{" +
            optionalId().map(f -> "id=" + f + ", ").orElse("") +
            optionalName().map(f -> "name=" + f + ", ").orElse("") +
            optionalDescription().map(f -> "description=" + f + ", ").orElse("") +
            optionalDate().map(f -> "date=" + f + ", ").orElse("") +
            optionalDistinct().map(f -> "distinct=" + f + ", ").orElse("") +
            "}";
    }
}
