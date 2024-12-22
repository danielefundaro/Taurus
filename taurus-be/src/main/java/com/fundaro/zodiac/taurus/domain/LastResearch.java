package com.fundaro.zodiac.taurus.domain;

import jakarta.validation.constraints.NotNull;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.ZonedDateTime;

/**
 * A LastResearch.
 */
@Table("last_research")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class LastResearch extends CommonFields {

    @NotNull(message = "must not be null")
    @Column("user_id")
    private String userId;

    @Column("value")
    private String value;

    @Column("field")
    private String field;

    public LastResearch() {
        super();
    }

    public LastResearch(CommonFields commonFields) {
        super(commonFields);
    }

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public LastResearch id(Long id) {
        this.setId(id);
        return this;
    }

    public LastResearch deleted(Boolean deleted) {
        this.setDeleted(deleted);
        return this;
    }

    public LastResearch insertBy(String insertBy) {
        this.setInsertBy(insertBy);
        return this;
    }

    public LastResearch insertDate(ZonedDateTime insertDate) {
        this.setInsertDate(insertDate);
        return this;
    }

    public LastResearch editBy(String editBy) {
        this.setEditBy(editBy);
        return this;
    }

    public LastResearch editDate(ZonedDateTime editDate) {
        this.setEditDate(editDate);
        return this;
    }

    public String getUserId() {
        return this.userId;
    }

    public LastResearch userId(String userId) {
        this.setUserId(userId);
        return this;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getValue() {
        return this.value;
    }

    public LastResearch value(String value) {
        this.setValue(value);
        return this;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getField() {
        return this.field;
    }

    public LastResearch field(String field) {
        this.setField(field);
        return this;
    }

    public void setField(String field) {
        this.field = field;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LastResearch)) {
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
        return "LastResearch{" +
            "id=" + getId() +
            ", userId='" + getUserId() + "'" +
            ", value='" + getValue() + "'" +
            ", field='" + getField() + "'" +
            "}";
    }
}
