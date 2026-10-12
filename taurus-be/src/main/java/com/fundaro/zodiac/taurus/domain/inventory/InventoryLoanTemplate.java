package com.fundaro.zodiac.taurus.domain.inventory;

import jakarta.persistence.*;

/**
 * Immutable version of the loan sheet template: a template uploaded by the tenant, or a copy of the Taurus default
 * stored when a sheet is issued with it.
 */
@Entity
@Table(name = "inventory_loan_template")
public class InventoryLoanTemplate extends AuditedEntity {

    @Column(name = "version_number")
    private Integer versionNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false, length = 16)
    private InventoryLoanTemplateOrigin origin;

    @Column(name = "default_label", length = 32)
    private String defaultLabel;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    public Integer getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(Integer versionNumber) {
        this.versionNumber = versionNumber;
    }

    public InventoryLoanTemplateOrigin getOrigin() {
        return origin;
    }

    public void setOrigin(InventoryLoanTemplateOrigin origin) {
        this.origin = origin;
    }

    public String getDefaultLabel() {
        return defaultLabel;
    }

    public void setDefaultLabel(String defaultLabel) {
        this.defaultLabel = defaultLabel;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }
}
