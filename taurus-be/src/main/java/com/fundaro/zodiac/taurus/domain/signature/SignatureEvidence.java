package com.fundaro.zodiac.taurus.domain.signature;

import com.fundaro.zodiac.taurus.domain.inventory.AuditedEntity;
import jakarta.persistence.*;

import java.time.ZonedDateTime;

/**
 * Immutable evidence of a signature, chained to the previous evidence of the tenant by hash. Only its personal data
 * can be removed, once, when the act is purged; a database trigger refuses every other change.
 */
@Entity
@Table(name = "signature_evidence")
public class SignatureEvidence extends AuditedEntity {

    @Column(name = "sequence", nullable = false, updatable = false)
    private long sequence;

    @Column(name = "act_id", nullable = false, updatable = false)
    private Long actId;

    @Column(name = "document_id", updatable = false)
    private Long documentId;

    @Column(name = "signer_id", updatable = false)
    private Long signerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 32, updatable = false)
    private SignaturePurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 24, updatable = false)
    private SignatureEvidenceMethod method;

    @Column(name = "document_hash", length = 64, updatable = false)
    private String documentHash;

    @Column(name = "declaration_hash", nullable = false, length = 64, updatable = false)
    private String declarationHash;

    @Column(name = "revocation_check", length = 16, updatable = false)
    private String revocationCheck;

    @Column(name = "challenge_id", updatable = false)
    private Long challengeId;

    @Column(name = "signed_at", nullable = false, updatable = false)
    private ZonedDateTime signedAt;

    @Column(name = "declaration_text", columnDefinition = "text")
    private String declarationText;

    @Column(name = "signer_name")
    private String signerName;

    @Column(name = "signer_keycloak_id")
    private String signerKeycloakId;

    @Column(name = "destination_email")
    private String destinationEmail;

    @Column(name = "certificate_subject", length = 1000)
    private String certificateSubject;

    @Column(name = "certificate_issuer", length = 1000)
    private String certificateIssuer;

    @Column(name = "certificate_serial", length = 128)
    private String certificateSerial;

    @Column(name = "certificate_tax_code", length = 32)
    private String certificateTaxCode;

    @Column(name = "session_id")
    private String sessionId;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "personal_data_hash", nullable = false, length = 64, updatable = false)
    private String personalDataHash;

    @Column(name = "previous_hash", nullable = false, length = 64, updatable = false)
    private String previousHash;

    @Column(name = "evidence_hash", nullable = false, length = 64, updatable = false)
    private String evidenceHash;

    @Column(name = "purged_at")
    private ZonedDateTime purgedAt;

    /**
     * Removes the personal data; hashes and links stay, so the chain remains verifiable.
     */
    public void purgePersonalData(ZonedDateTime at, String actor) {
        declarationText = null;
        signerName = null;
        signerKeycloakId = null;
        destinationEmail = null;
        certificateSubject = null;
        certificateIssuer = null;
        certificateSerial = null;
        certificateTaxCode = null;
        sessionId = null;
        ipAddress = null;
        userAgent = null;
        purgedAt = at;
        touchAudit(actor);
    }

    public long getSequence() {
        return sequence;
    }

    public void setSequence(long sequence) {
        this.sequence = sequence;
    }

    public Long getActId() {
        return actId;
    }

    public void setActId(Long actId) {
        this.actId = actId;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public Long getSignerId() {
        return signerId;
    }

    public void setSignerId(Long signerId) {
        this.signerId = signerId;
    }

    public SignaturePurpose getPurpose() {
        return purpose;
    }

    public void setPurpose(SignaturePurpose purpose) {
        this.purpose = purpose;
    }

    public SignatureEvidenceMethod getMethod() {
        return method;
    }

    public void setMethod(SignatureEvidenceMethod method) {
        this.method = method;
    }

    public String getDocumentHash() {
        return documentHash;
    }

    public void setDocumentHash(String documentHash) {
        this.documentHash = documentHash;
    }

    public String getDeclarationHash() {
        return declarationHash;
    }

    public void setDeclarationHash(String declarationHash) {
        this.declarationHash = declarationHash;
    }

    public String getRevocationCheck() {
        return revocationCheck;
    }

    public void setRevocationCheck(String revocationCheck) {
        this.revocationCheck = revocationCheck;
    }

    public Long getChallengeId() {
        return challengeId;
    }

    public void setChallengeId(Long challengeId) {
        this.challengeId = challengeId;
    }

    public ZonedDateTime getSignedAt() {
        return signedAt;
    }

    public void setSignedAt(ZonedDateTime signedAt) {
        this.signedAt = signedAt;
    }

    public String getDeclarationText() {
        return declarationText;
    }

    public void setDeclarationText(String declarationText) {
        this.declarationText = declarationText;
    }

    public String getSignerName() {
        return signerName;
    }

    public void setSignerName(String signerName) {
        this.signerName = signerName;
    }

    public String getSignerKeycloakId() {
        return signerKeycloakId;
    }

    public void setSignerKeycloakId(String signerKeycloakId) {
        this.signerKeycloakId = signerKeycloakId;
    }

    public String getDestinationEmail() {
        return destinationEmail;
    }

    public void setDestinationEmail(String destinationEmail) {
        this.destinationEmail = destinationEmail;
    }

    public String getCertificateSubject() {
        return certificateSubject;
    }

    public void setCertificateSubject(String certificateSubject) {
        this.certificateSubject = certificateSubject;
    }

    public String getCertificateIssuer() {
        return certificateIssuer;
    }

    public void setCertificateIssuer(String certificateIssuer) {
        this.certificateIssuer = certificateIssuer;
    }

    public String getCertificateSerial() {
        return certificateSerial;
    }

    public void setCertificateSerial(String certificateSerial) {
        this.certificateSerial = certificateSerial;
    }

    public String getCertificateTaxCode() {
        return certificateTaxCode;
    }

    public void setCertificateTaxCode(String certificateTaxCode) {
        this.certificateTaxCode = certificateTaxCode;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public String getPersonalDataHash() {
        return personalDataHash;
    }

    public void setPersonalDataHash(String personalDataHash) {
        this.personalDataHash = personalDataHash;
    }

    public String getPreviousHash() {
        return previousHash;
    }

    public void setPreviousHash(String previousHash) {
        this.previousHash = previousHash;
    }

    public String getEvidenceHash() {
        return evidenceHash;
    }

    public void setEvidenceHash(String evidenceHash) {
        this.evidenceHash = evidenceHash;
    }

    public ZonedDateTime getPurgedAt() {
        return purgedAt;
    }

    public void setPurgedAt(ZonedDateTime purgedAt) {
        this.purgedAt = purgedAt;
    }
}
