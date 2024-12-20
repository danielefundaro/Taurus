package com.fundaro.zodiac.taurus.domain;

import jakarta.validation.constraints.NotNull;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.ZonedDateTime;

/**
 * A Preferences.
 */
@Table("preferences")
@SuppressWarnings("common-java:DuplicatedBlocks")
public class Preferences extends CommonFields {

    @NotNull(message = "must not be null")
    @Column("user_id")
    private String userId;

    @NotNull(message = "must not be null")
    @Column("key")
    private String key;

    @Column("value")
    private String value;

    public Preferences() {
        super();
    }

    public Preferences(CommonFields commonFields) {
        super(commonFields);
    }

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Preferences id(Long id) {
        this.setId(id);
        return this;
    }

    public Preferences deleted(Boolean deleted) {
        this.setDeleted(deleted);
        return this;
    }

    public Preferences insertBy(String insertBy) {
        this.setInsertBy(insertBy);
        return this;
    }

    public Preferences insertDate(ZonedDateTime insertDate) {
        this.setInsertDate(insertDate);
        return this;
    }

    public Preferences editBy(String editBy) {
        this.setEditBy(editBy);
        return this;
    }

    public Preferences editDate(ZonedDateTime editDate) {
        this.setEditDate(editDate);
        return this;
    }

    public String getUserId() {
        return this.userId;
    }

    public Preferences userId(String userId) {
        this.setUserId(userId);
        return this;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getKey() {
        return this.key;
    }

    public Preferences key(String key) {
        this.setKey(key);
        return this;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return this.value;
    }

    public Preferences value(String value) {
        this.setValue(value);
        return this;
    }

    public void setValue(String value) {
        this.value = value;
    }

    // jhipster-needle-entity-add-getters-setters - JHipster will add getters and setters here

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Preferences)) {
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
        return "Preferences{" +
            "id=" + getId() +
            ", deleted='" + getDeleted() + "'" +
            ", insertBy='" + getInsertBy() + "'" +
            ", insertDate='" + getInsertDate() + "'" +
            ", editBy='" + getEditBy() + "'" +
            ", editDate='" + getEditDate() + "'" +
            ", userId='" + getUserId() + "'" +
            ", key='" + getKey() + "'" +
            ", value='" + getValue() + "'" +
            "}";
    }
}
