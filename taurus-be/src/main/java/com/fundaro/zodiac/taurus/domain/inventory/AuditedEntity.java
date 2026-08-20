package com.fundaro.zodiac.taurus.domain.inventory;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import java.time.ZonedDateTime;

@MappedSuperclass
public abstract class AuditedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "deleted", nullable = false)
    private boolean deleted;

    @Column(name = "insert_date", nullable = false, updatable = false)
    private ZonedDateTime insertDate;

    @Column(name = "insert_by", nullable = false, updatable = false)
    private String insertBy;

    @Column(name = "edit_date", nullable = false)
    private ZonedDateTime editDate;

    @Column(name = "edit_by", nullable = false)
    private String editBy;

    public void initializeAudit(String actor) {
        ZonedDateTime now = ZonedDateTime.now();
        deleted = false;
        insertDate = now;
        insertBy = actor;
        editDate = now;
        editBy = actor;
    }

    public void touchAudit(String actor) {
        editDate = ZonedDateTime.now();
        editBy = actor;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }
    public ZonedDateTime getInsertDate() { return insertDate; }
    public void setInsertDate(ZonedDateTime insertDate) { this.insertDate = insertDate; }
    public String getInsertBy() { return insertBy; }
    public void setInsertBy(String insertBy) { this.insertBy = insertBy; }
    public ZonedDateTime getEditDate() { return editDate; }
    public void setEditDate(ZonedDateTime editDate) { this.editDate = editDate; }
    public String getEditBy() { return editBy; }
    public void setEditBy(String editBy) { this.editBy = editBy; }
}
