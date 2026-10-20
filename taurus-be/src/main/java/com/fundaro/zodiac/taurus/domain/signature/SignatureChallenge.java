package com.fundaro.zodiac.taurus.domain.signature;

import com.fundaro.zodiac.taurus.domain.inventory.TenantAuditedEntity;
import jakarta.persistence.*;

import java.time.ZonedDateTime;

/**
 * Request of a confirmation code, bound to the signer, to the purpose and to the hash of the confirmed items. Only
 * the HMAC of the code is stored.
 */
@Entity
@Table(name = "signature_challenge")
public class SignatureChallenge extends TenantAuditedEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 32)
    private SignaturePurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(name = "method", nullable = false, length = 16)
    private SignatureMethod method;

    @Column(name = "user_keycloak_id")
    private String userKeycloakId;

    /**
     * Link of a parent or guardian without an account; {@code null} for the challenges of an authenticated user.
     */
    @Column(name = "link_id")
    private Long linkId;

    @Column(name = "destination_email")
    private String destinationEmail;

    @Column(name = "code_digest", length = 64)
    private String codeDigest;

    @Column(name = "items_json", nullable = false, columnDefinition = "text")
    private String itemsJson;

    @Column(name = "items_hash", nullable = false, length = 64)
    private String itemsHash;

    @Column(name = "expires_at", nullable = false)
    private ZonedDateTime expiresAt;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "sends", nullable = false)
    private int sends;

    @Column(name = "last_sent_at")
    private ZonedDateTime lastSentAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private SignatureChallengeStatus status;

    @Column(name = "confirmed_at")
    private ZonedDateTime confirmedAt;

    public SignaturePurpose getPurpose() {
        return purpose;
    }

    public void setPurpose(SignaturePurpose purpose) {
        this.purpose = purpose;
    }

    public SignatureMethod getMethod() {
        return method;
    }

    public void setMethod(SignatureMethod method) {
        this.method = method;
    }

    public String getUserKeycloakId() {
        return userKeycloakId;
    }

    public void setUserKeycloakId(String userKeycloakId) {
        this.userKeycloakId = userKeycloakId;
    }

    public Long getLinkId() {
        return linkId;
    }

    public void setLinkId(Long linkId) {
        this.linkId = linkId;
    }

    public String getDestinationEmail() {
        return destinationEmail;
    }

    public void setDestinationEmail(String destinationEmail) {
        this.destinationEmail = destinationEmail;
    }

    public String getCodeDigest() {
        return codeDigest;
    }

    public void setCodeDigest(String codeDigest) {
        this.codeDigest = codeDigest;
    }

    public String getItemsJson() {
        return itemsJson;
    }

    public void setItemsJson(String itemsJson) {
        this.itemsJson = itemsJson;
    }

    public String getItemsHash() {
        return itemsHash;
    }

    public void setItemsHash(String itemsHash) {
        this.itemsHash = itemsHash;
    }

    public ZonedDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(ZonedDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public int getSends() {
        return sends;
    }

    public void setSends(int sends) {
        this.sends = sends;
    }

    public ZonedDateTime getLastSentAt() {
        return lastSentAt;
    }

    public void setLastSentAt(ZonedDateTime lastSentAt) {
        this.lastSentAt = lastSentAt;
    }

    public SignatureChallengeStatus getStatus() {
        return status;
    }

    public void setStatus(SignatureChallengeStatus status) {
        this.status = status;
    }

    public ZonedDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(ZonedDateTime confirmedAt) {
        this.confirmedAt = confirmedAt;
    }
}
