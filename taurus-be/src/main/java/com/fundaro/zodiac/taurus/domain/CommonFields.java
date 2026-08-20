package com.fundaro.zodiac.taurus.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;

import java.io.Serial;
import java.io.Serializable;
import java.time.ZonedDateTime;

@MappedSuperclass
public class CommonFields implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @NotNull(message = "must not be null")
    @Column(name = "deleted")
    private Boolean deleted;

    @NotNull(message = "must not be null")
    @Column(name = "insert_by")
    private String insertBy;

    @NotNull(message = "must not be null")
    @Column(name = "insert_date")
    private ZonedDateTime insertDate;

    @NotNull(message = "must not be null")
    @Column(name = "edit_by")
    private String editBy;

    @NotNull(message = "must not be null")
    @Column(name = "edit_date")
    private ZonedDateTime editDate;

    @NotNull(message = "must not be null")
    @Column(name = "user_id")
    private String userId;

    public CommonFields() {
    }

    public CommonFields(CommonFields other) {
        this.id = other.id;
        this.deleted = other.deleted;
        this.insertBy = other.insertBy;
        this.insertDate = other.insertDate;
        this.editBy = other.editBy;
        this.editDate = other.editDate;
        this.userId = other.userId;
    }

    // jhipster-needle-entity-add-field - JHipster will add fields here

    public Long getId() {
        return this.id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Boolean getDeleted() {
        return this.deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }

    public String getInsertBy() {
        return this.insertBy;
    }

    public void setInsertBy(String insertBy) {
        this.insertBy = insertBy;
    }

    public ZonedDateTime getInsertDate() {
        return this.insertDate;
    }

    public void setInsertDate(ZonedDateTime insertDate) {
        this.insertDate = insertDate;
    }

    public String getEditBy() {
        return this.editBy;
    }

    public void setEditBy(String editBy) {
        this.editBy = editBy;
    }

    public ZonedDateTime getEditDate() {
        return this.editDate;
    }

    public void setEditDate(ZonedDateTime editDate) {
        this.editDate = editDate;
    }

    public String getUserId() {
        return this.userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof CommonFields)) {
            return false;
        }

        return getId() != null && getId().equals(((CommonFields) o).getId());
    }
}
