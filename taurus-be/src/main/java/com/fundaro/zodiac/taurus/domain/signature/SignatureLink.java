package com.fundaro.zodiac.taurus.domain.signature;

import com.fundaro.zodiac.taurus.domain.inventory.TenantAuditedEntity;
import jakarta.persistence.*;

import java.time.ZonedDateTime;

/**
 * Link sent by e-mail to a parent or guardian without an account, for one signer of one act. Only a fingerprint of the
 * token is kept: its digest is in the public registry, which resolves the tenant.
 */
@Entity
@Table(name = "signature_link")
public class SignatureLink extends TenantAuditedEntity {

    @Column(name = "signer_id", nullable = false)
    private Long signerId;

    @Column(name = "token_fingerprint", nullable = false, length = 8)
    private String tokenFingerprint;

    @Column(name = "expires_at", nullable = false)
    private ZonedDateTime expiresAt;

    @Column(name = "revoked_at")
    private ZonedDateTime revokedAt;

    /**
     * When the SMTP server accepted the e-mail with the link; {@code null} while it has not been sent.
     */
    @Column(name = "sent_at")
    private ZonedDateTime sentAt;

    @Column(name = "last_used_at")
    private ZonedDateTime lastUsedAt;

    @Column(name = "expiry_notified_at")
    private ZonedDateTime expiryNotifiedAt;

    public boolean isActive(ZonedDateTime now) {
        return !isDeleted() && revokedAt == null && now.isBefore(expiresAt);
    }

    public Long getSignerId() {
        return signerId;
    }

    public void setSignerId(Long signerId) {
        this.signerId = signerId;
    }

    public String getTokenFingerprint() {
        return tokenFingerprint;
    }

    public void setTokenFingerprint(String tokenFingerprint) {
        this.tokenFingerprint = tokenFingerprint;
    }

    public ZonedDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(ZonedDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public ZonedDateTime getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(ZonedDateTime revokedAt) {
        this.revokedAt = revokedAt;
    }

    public ZonedDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(ZonedDateTime sentAt) {
        this.sentAt = sentAt;
    }

    public ZonedDateTime getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(ZonedDateTime lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    public ZonedDateTime getExpiryNotifiedAt() {
        return expiryNotifiedAt;
    }

    public void setExpiryNotifiedAt(ZonedDateTime expiryNotifiedAt) {
        this.expiryNotifiedAt = expiryNotifiedAt;
    }
}
