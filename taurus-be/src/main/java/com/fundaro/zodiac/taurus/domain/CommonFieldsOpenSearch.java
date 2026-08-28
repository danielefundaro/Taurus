package com.fundaro.zodiac.taurus.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;

import java.io.Serializable;
import java.util.Date;

@JsonInclude(JsonInclude.Include.NON_NULL)
@MappedSuperclass
public class CommonFieldsOpenSearch implements Serializable {

    @JsonIgnore
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", updatable = false, nullable = false)
    private Long id;

    @Column(name = "deleted", nullable = false)
    private Boolean deleted;

    @JsonProperty("insert_by")
    @Column(name = "insert_by")
    private String insertBy;

    @JsonProperty("insert_date")
    @Column(name = "insert_date", nullable = false)
    private Date insertDate;

    @JsonProperty("edit_by")
    @Column(name = "edit_by")
    private String editBy;

    @JsonProperty("edit_date")
    @Column(name = "edit_date", nullable = false)
    private Date editDate;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Version
    @Column(name = "entity_version", nullable = false)
    private Long entityVersion;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }

    public String getInsertBy() {
        return insertBy;
    }

    public void setInsertBy(String insertBy) {
        this.insertBy = insertBy;
    }

    public Date getInsertDate() {
        return insertDate;
    }

    public void setInsertDate(Date insertDate) {
        this.insertDate = insertDate;
    }

    public String getEditBy() {
        return editBy;
    }

    public void setEditBy(String editBy) {
        this.editBy = editBy;
    }

    public Date getEditDate() {
        return editDate;
    }

    public void setEditDate(Date editDate) {
        this.editDate = editDate;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getEntityVersion() {
        return entityVersion;
    }

    public void setEntityVersion(Long entityVersion) {
        this.entityVersion = entityVersion;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof CommonFieldsOpenSearch)) {
            return false;
        }

        return getId() != null && getId().equals(((CommonFieldsOpenSearch) o).getId());
    }
}
