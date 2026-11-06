package com.fundaro.zodiac.taurus.domain;

import com.fundaro.zodiac.taurus.domain.enumeration.LegalDocumentAction;
import com.fundaro.zodiac.taurus.domain.enumeration.LegalDocumentType;
import jakarta.persistence.*;

import java.time.ZonedDateTime;

@Entity
@Table(
    name = "legal_document",
    schema = "public",
    uniqueConstraints = @UniqueConstraint(name = "uq_legal_document_type_version", columnNames = {"document_type", "version"})
)
public class LegalDocument extends AuditFields {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 32)
    private LegalDocumentType documentType;

    @Column(name = "version", nullable = false, length = 32)
    private String version;

    @Column(name = "title", nullable = false)
    private String title;

    /** Address of a document published before the text lived in Taurus; empty for the new ones. */
    @Column(name = "url", length = 2048)
    private String url;

    /** Markdown text, normalized; empty only for a document published before the text lived in Taurus. */
    @Column(name = "content", columnDefinition = "text")
    private String content;

    @Column(name = "content_sha256", length = 64)
    private String contentSha256;

    @Column(name = "change_summary", length = 1000)
    private String changeSummary;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 32)
    private LegalDocumentAction action;

    /** First activation; empty while the version is a draft. */
    @Column(name = "published_at")
    private ZonedDateTime publishedAt;

    @Column(name = "active", nullable = false)
    private Boolean active;

    @Column(name = "required", nullable = false)
    private Boolean required;

    @Column(name = "created_at", nullable = false)
    private ZonedDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private ZonedDateTime updatedAt;

    @PrePersist
    void prePersist() {
        ZonedDateTime now = ZonedDateTime.now();
        createdAt = createdAt == null ? now : createdAt;
        updatedAt = now;
        active = active == null ? Boolean.FALSE : active;
        required = required == null ? Boolean.TRUE : required;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = ZonedDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LegalDocumentType getDocumentType() {
        return documentType;
    }

    public void setDocumentType(LegalDocumentType documentType) {
        this.documentType = documentType;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getContentSha256() {
        return contentSha256;
    }

    public void setContentSha256(String contentSha256) {
        this.contentSha256 = contentSha256;
    }

    public String getChangeSummary() {
        return changeSummary;
    }

    public void setChangeSummary(String changeSummary) {
        this.changeSummary = changeSummary;
    }

    public LegalDocumentAction getAction() {
        return action;
    }

    public void setAction(LegalDocumentAction action) {
        this.action = action;
    }

    public ZonedDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(ZonedDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Boolean getRequired() {
        return required;
    }

    public void setRequired(Boolean required) {
        this.required = required;
    }

    /** A draft has never been published: it is the only state where the version can change. */
    public boolean isDraft() {
        return publishedAt == null;
    }

    /** A document published with its URL only, before the text lived in Taurus. */
    public boolean isWithoutContent() {
        return content == null;
    }

    public ZonedDateTime getCreatedAt() {
        return createdAt;
    }

    public ZonedDateTime getUpdatedAt() {
        return updatedAt;
    }
}
