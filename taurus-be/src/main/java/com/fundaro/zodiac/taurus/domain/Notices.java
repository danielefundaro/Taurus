package com.fundaro.zodiac.taurus.domain;

import jakarta.validation.constraints.NotNull;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.ZonedDateTime;

/**
 * A Notices.
 */
@Table("notices")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Notices extends CommonFields {

    @NotNull(message = "must not be null")
    @Column("name")
    private String name;

    @Column("message")
    private String message;

    @Column("read_date")
    private ZonedDateTime readDate;

    public Notices() {
        super();
    }

    public Notices(CommonFields commonFields) {
        super(commonFields);
    }

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Notices id(Long id) {
        this.setId(id);
        return this;
    }

    public Notices deleted(Boolean deleted) {
        this.setDeleted(deleted);
        return this;
    }

    public Notices insertBy(String insertBy) {
        this.setInsertBy(insertBy);
        return this;
    }

    public Notices insertDate(ZonedDateTime insertDate) {
        this.setInsertDate(insertDate);
        return this;
    }

    public Notices editBy(String editBy) {
        this.setEditBy(editBy);
        return this;
    }

    public Notices editDate(ZonedDateTime editDate) {
        this.setEditDate(editDate);
        return this;
    }

    public Notices userId(String userId) {
        this.setUserId(userId);
        return this;
    }

    public String getName() {
        return this.name;
    }

    public Notices name(String name) {
        this.setName(name);
        return this;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMessage() {
        return this.message;
    }

    public Notices message(String message) {
        this.setMessage(message);
        return this;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public ZonedDateTime getReadDate() {
        return this.readDate;
    }

    public Notices readDate(ZonedDateTime readDate) {
        this.setReadDate(readDate);
        return this;
    }

    public void setReadDate(ZonedDateTime readDate) {
        this.readDate = readDate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Notices)) {
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
        return "Notices{" +
            "id=" + getId() +
            ", userId='" + getUserId() + "'" +
            ", name='" + getName() + "'" +
            ", message='" + getMessage() + "'" +
            ", readDate='" + getReadDate() + "'" +
            "}";
    }
}
