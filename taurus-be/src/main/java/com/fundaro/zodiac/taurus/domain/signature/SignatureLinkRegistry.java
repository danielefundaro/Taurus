package com.fundaro.zodiac.taurus.domain.signature;

import jakarta.persistence.*;

import java.time.ZonedDateTime;

/**
 * Public registry of the signature links: from the SHA-256 digest of a token to the tenant and the link. It holds no
 * personal data.
 */
@Entity
@Table(name = "signature_link_registry", schema = "public")
public class SignatureLinkRegistry {

    @Id
    @Column(name = "token_digest", length = 64)
    private String tokenDigest;

    @Column(name = "tenant_id", nullable = false)
    private Long tenantId;

    @Column(name = "link_id", nullable = false)
    private Long linkId;

    @Column(name = "expires_at", nullable = false)
    private ZonedDateTime expiresAt;

    @Column(name = "revoked_at")
    private ZonedDateTime revokedAt;

    public String getTokenDigest() {
        return tokenDigest;
    }

    public void setTokenDigest(String tokenDigest) {
        this.tokenDigest = tokenDigest;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public Long getLinkId() {
        return linkId;
    }

    public void setLinkId(Long linkId) {
        this.linkId = linkId;
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
}
