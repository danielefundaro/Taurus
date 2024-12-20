package com.fundaro.zodiac.taurus.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fundaro.zodiac.taurus.domain.enumeration.PieceTypeEnum;
import jakarta.validation.constraints.NotNull;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.ZonedDateTime;

/**
 * A Pieces.
 */
@Table("pieces")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Pieces extends CommonFields {

    @NotNull(message = "must not be null")
    @Column("name")
    private String name;

    @Column("description")
    private String description;

    @NotNull(message = "must not be null")
    @Column("type")
    private PieceTypeEnum type;

    @NotNull(message = "must not be null")
    @Column("content_type")
    private String contentType;

    @NotNull(message = "must not be null")
    @Column("path")
    private String path;

    @Column("order_number")
    private Long orderNumber;

    @org.springframework.data.annotation.Transient
    @JsonIgnoreProperties(value = {"pieces", "instrument", "track"}, allowSetters = true)
    private Media media;

    @Column("media_id")
    private Long mediaId;

    public Pieces() {
        super();
    }

    public Pieces(CommonFields commonFields) {
        super(commonFields);
    }

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Pieces id(Long id) {
        this.setId(id);
        return this;
    }

    public Pieces deleted(Boolean deleted) {
        this.setDeleted(deleted);
        return this;
    }

    public Pieces insertBy(String insertBy) {
        this.setInsertBy(insertBy);
        return this;
    }

    public Pieces insertDate(ZonedDateTime insertDate) {
        this.setInsertDate(insertDate);
        return this;
    }

    public Pieces editBy(String editBy) {
        this.setEditBy(editBy);
        return this;
    }

    public Pieces editDate(ZonedDateTime editDate) {
        this.setEditDate(editDate);
        return this;
    }

    public String getName() {
        return this.name;
    }

    public Pieces name(String name) {
        this.setName(name);
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return this.description;
    }

    public Pieces description(String description) {
        this.setDescription(description);
        return this;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public PieceTypeEnum getType() {
        return this.type;
    }

    public Pieces type(PieceTypeEnum type) {
        this.setType(type);
        return this;
    }

    public void setType(PieceTypeEnum type) {
        this.type = type;
    }

    public String getContentType() {
        return this.contentType;
    }

    public Pieces contentType(String contentType) {
        this.setContentType(contentType);
        return this;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getPath() {
        return this.path;
    }

    public Pieces path(String path) {
        this.setPath(path);
        return this;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Long getOrderNumber() {
        return this.orderNumber;
    }

    public Pieces orderNumber(Long orderNumber) {
        this.setOrderNumber(orderNumber);
        return this;
    }

    public void setOrderNumber(Long orderNumber) {
        this.orderNumber = orderNumber;
    }

    public Media getMedia() {
        return this.media;
    }

    public void setMedia(Media media) {
        this.media = media;
        this.mediaId = media != null ? media.getId() : null;
    }

    public Pieces media(Media media) {
        this.setMedia(media);
        return this;
    }

    public Long getMediaId() {
        return this.mediaId;
    }

    public void setMediaId(Long media) {
        this.mediaId = media;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Pieces)) {
            return false;
        }

        return super.equals(o);
    }

    @Override
    public int hashCode() {
        // see https://vladmihalcea.com/how-to-implement-equals-and-hashcode-using-the-jpa-entity-identifier/
        return getClass().hashCode();
    }

    // prettier-ignore
    @Override
    public String toString() {
        return "Pieces{" +
            "id=" + getId() +
            ", deleted='" + getDeleted() + "'" +
            ", insertBy='" + getInsertBy() + "'" +
            ", insertDate='" + getInsertDate() + "'" +
            ", editBy='" + getEditBy() + "'" +
            ", editDate='" + getEditDate() + "'" +
            ", name='" + getName() + "'" +
            ", description='" + getDescription() + "'" +
            ", type='" + getType() + "'" +
            ", contentType='" + getContentType() + "'" +
            ", path='" + getPath() + "'" +
            ", orderNumber=" + getOrderNumber() +
            "}";
    }
}
