package com.fundaro.zodiac.taurus.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import java.time.ZonedDateTime;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

/**
 * Persistence-only audit metadata. These fields must never be exposed through DTOs.
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class AuditFields {

    @JsonIgnore
    @Column(name = "deleted", nullable = false)
    private boolean deleted;

    @JsonIgnore
    @CreatedBy
    @Column(name = "insert_by", nullable = false, updatable = false)
    private String insertBy = "system";

    @JsonIgnore
    @CreatedDate
    @Column(name = "insert_date", nullable = false, updatable = false)
    private ZonedDateTime insertDate = ZonedDateTime.now();

    @JsonIgnore
    @LastModifiedBy
    @Column(name = "edit_by", nullable = false)
    private String editBy = "system";

    @JsonIgnore
    @LastModifiedDate
    @Column(name = "edit_date", nullable = false)
    private ZonedDateTime editDate = ZonedDateTime.now();

    public void initializeAudit(String actor) {
        ZonedDateTime now = ZonedDateTime.now();
        deleted = false;
        insertBy = actor;
        insertDate = now;
        editBy = actor;
        editDate = now;
    }

    public void touchAudit(String actor) {
        editBy = actor;
        editDate = ZonedDateTime.now();
    }

    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }
    public String getInsertBy() { return insertBy; }
    public void setInsertBy(String insertBy) { this.insertBy = insertBy; }
    public ZonedDateTime getInsertDate() { return insertDate; }
    public void setInsertDate(ZonedDateTime insertDate) { this.insertDate = insertDate; }
    public String getEditBy() { return editBy; }
    public void setEditBy(String editBy) { this.editBy = editBy; }
    public ZonedDateTime getEditDate() { return editDate; }
    public void setEditDate(ZonedDateTime editDate) { this.editDate = editDate; }
}
