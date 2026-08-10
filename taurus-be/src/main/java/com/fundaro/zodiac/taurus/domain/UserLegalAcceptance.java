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
}
