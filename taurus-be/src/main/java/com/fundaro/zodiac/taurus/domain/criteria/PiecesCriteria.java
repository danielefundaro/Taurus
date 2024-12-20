package com.fundaro.zodiac.taurus.domain.criteria;

import com.fundaro.zodiac.taurus.domain.enumeration.PieceTypeEnum;
import org.springdoc.core.annotations.ParameterObject;
import tech.jhipster.service.filter.Filter;
import tech.jhipster.service.filter.LongFilter;
import tech.jhipster.service.filter.StringFilter;

import java.util.Objects;
import java.util.Optional;

/**
 * Criteria class for the {@link com.fundaro.zodiac.taurus.domain.Pieces} entity. This class is used
 * in {@link com.fundaro.zodiac.taurus.web.rest.PiecesResource} to receive all the possible filtering options from
 * the Http GET request parameters.
 * For example the following could be a valid request:
 * {@code /pieces?id.greaterThan=5&attr1.contains=something&attr2.specified=false}
 * As Spring is unable to properly convert the types, unless specific {@link Filter} class are used, we need to use
 * fix type specific filters.
 */
@ParameterObject
@SuppressWarnings("common-java:DuplicatedBlocks")
public class PiecesCriteria extends CommonCriteria {

    /**
     * Class for filtering PieceTypeEnum
     */
    public static class PieceTypeEnumFilter extends Filter<PieceTypeEnum> {

        public PieceTypeEnumFilter() {
        }

        public PieceTypeEnumFilter(PieceTypeEnumFilter filter) {
            super(filter);
        }

        @Override
        public PieceTypeEnumFilter copy() {
            return new PieceTypeEnumFilter(this);
        }
    }

    private StringFilter name;

    private StringFilter description;

    private PieceTypeEnumFilter type;

    private StringFilter contentType;

    private StringFilter path;

    private LongFilter orderNumber;

    private LongFilter mediaId;

    public PiecesCriteria() {
        super();
    }

    public PiecesCriteria(PiecesCriteria other) {
        super(other);
        this.name = other.optionalName().map(StringFilter::copy).orElse(null);
        this.description = other.optionalDescription().map(StringFilter::copy).orElse(null);
        this.type = other.optionalType().map(PieceTypeEnumFilter::copy).orElse(null);
        this.contentType = other.optionalContentType().map(StringFilter::copy).orElse(null);
        this.path = other.optionalPath().map(StringFilter::copy).orElse(null);
        this.orderNumber = other.optionalOrderNumber().map(LongFilter::copy).orElse(null);
        this.mediaId = other.optionalMediaId().map(LongFilter::copy).orElse(null);
    }

    @Override
    public PiecesCriteria copy() {
        return new PiecesCriteria(this);
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

    public PieceTypeEnumFilter getType() {
        return type;
    }

    public Optional<PieceTypeEnumFilter> optionalType() {
        return Optional.ofNullable(type);
    }

    public PieceTypeEnumFilter type() {
        if (type == null) {
            setType(new PieceTypeEnumFilter());
        }
        return type;
    }

    public void setType(PieceTypeEnumFilter type) {
        this.type = type;
    }

    public StringFilter getContentType() {
        return contentType;
    }

    public Optional<StringFilter> optionalContentType() {
        return Optional.ofNullable(contentType);
    }

    public StringFilter contentType() {
        if (contentType == null) {
            setContentType(new StringFilter());
        }
        return contentType;
    }

    public void setContentType(StringFilter contentType) {
        this.contentType = contentType;
    }

    public StringFilter getPath() {
        return path;
    }

    public Optional<StringFilter> optionalPath() {
        return Optional.ofNullable(path);
    }

    public StringFilter path() {
        if (path == null) {
            setPath(new StringFilter());
        }
        return path;
    }

    public void setPath(StringFilter path) {
        this.path = path;
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

    public LongFilter getMediaId() {
        return mediaId;
    }

    public Optional<LongFilter> optionalMediaId() {
        return Optional.ofNullable(mediaId);
    }

    public LongFilter mediaId() {
        if (mediaId == null) {
            setMediaId(new LongFilter());
        }
        return mediaId;
    }

    public void setMediaId(LongFilter mediaId) {
        this.mediaId = mediaId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final PiecesCriteria that = (PiecesCriteria) o;
        return (
            super.equals(o) &&
                Objects.equals(name, that.name) &&
                Objects.equals(description, that.description) &&
                Objects.equals(type, that.type) &&
                Objects.equals(contentType, that.contentType) &&
                Objects.equals(path, that.path) &&
                Objects.equals(orderNumber, that.orderNumber) &&
                Objects.equals(mediaId, that.mediaId)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), name, description, type, contentType, path, orderNumber, mediaId);
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "PiecesCriteria{" +
            optionalId().map(f -> "id=" + f + ", ").orElse("") +
            optionalName().map(f -> "name=" + f + ", ").orElse("") +
            optionalDescription().map(f -> "description=" + f + ", ").orElse("") +
            optionalType().map(f -> "type=" + f + ", ").orElse("") +
            optionalContentType().map(f -> "contentType=" + f + ", ").orElse("") +
            optionalPath().map(f -> "path=" + f + ", ").orElse("") +
            optionalOrderNumber().map(f -> "orderNumber=" + f + ", ").orElse("") +
            optionalMediaId().map(f -> "mediaId=" + f + ", ").orElse("") +
            optionalDistinct().map(f -> "distinct=" + f + ", ").orElse("") +
            "}";
    }
}
