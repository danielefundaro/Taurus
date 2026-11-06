package com.fundaro.zodiac.taurus.domain;

import jakarta.persistence.*;

import java.time.ZonedDateTime;

@Entity
@Table(
    name = "user_legal_acceptance",
    uniqueConstraints = @UniqueConstraint(name = "uq_user_legal_document", columnNames = {"user_id", "legal_document_id"})
)
public class UserLegalAcceptance extends CommonFields {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "legal_document_id", nullable = false)
    private LegalDocument legalDocument;

    @jakarta.persistence.Column(name = "accepted_at", nullable = false)
    private ZonedDateTime acceptedAt;

    /** Fingerprint of the confirmed text; empty for a document without a text. */
    @jakarta.persistence.Column(name = "content_sha256", length = 64)
    private String contentSha256;

    public LegalDocument getLegalDocument() {
        return legalDocument;
    }

    public void setLegalDocument(LegalDocument legalDocument) {
        this.legalDocument = legalDocument;
    }

    public ZonedDateTime getAcceptedAt() {
        return acceptedAt;
    }

    public void setAcceptedAt(ZonedDateTime acceptedAt) {
        this.acceptedAt = acceptedAt;
    }

    public String getContentSha256() {
        return contentSha256;
    }

    public void setContentSha256(String contentSha256) {
        this.contentSha256 = contentSha256;
    }
}
