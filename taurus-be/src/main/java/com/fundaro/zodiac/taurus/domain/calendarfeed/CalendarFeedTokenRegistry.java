package com.fundaro.zodiac.taurus.domain.calendarfeed;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "calendar_feed_token_registry", schema = "public")
public class CalendarFeedTokenRegistry {
    @Id @Column(name = "token_digest", columnDefinition = "bytea") private byte[] tokenDigest;
    @Column(name = "subscription_id", nullable = false) private UUID subscriptionId;
    @Column(name = "tenant_id", nullable = false) private Long tenantId;
    @Column(name = "token_version", nullable = false) private int tokenVersion;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private CalendarFeedStatus status;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "revoked_at") private Instant revokedAt;
    public byte[] getTokenDigest() { return tokenDigest; } public void setTokenDigest(byte[] v) { tokenDigest = v; }
    public UUID getSubscriptionId() { return subscriptionId; } public void setSubscriptionId(UUID v) { subscriptionId = v; }
    public Long getTenantId() { return tenantId; } public void setTenantId(Long v) { tenantId = v; }
    public int getTokenVersion() { return tokenVersion; } public void setTokenVersion(int v) { tokenVersion = v; }
    public CalendarFeedStatus getStatus() { return status; } public void setStatus(CalendarFeedStatus v) { status = v; }
    public Instant getCreatedAt() { return createdAt; } public void setCreatedAt(Instant v) { createdAt = v; }
    public Instant getRevokedAt() { return revokedAt; } public void setRevokedAt(Instant v) { revokedAt = v; }
}
