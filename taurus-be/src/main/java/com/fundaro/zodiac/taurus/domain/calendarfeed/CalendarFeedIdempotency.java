package com.fundaro.zodiac.taurus.domain.calendarfeed;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "calendar_feed_idempotency")
public class CalendarFeedIdempotency {
    @Id
    @Column(name = "request_digest", columnDefinition = "bytea")
    private byte[] requestDigest;
    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;
    @Column(name = "request_fingerprint", nullable = false, columnDefinition = "bytea")
    private byte[] requestFingerprint;
    @Column(name = "token_version", nullable = false)
    private int tokenVersion;
    @Column(name = "token_ciphertext", nullable = false, columnDefinition = "bytea")
    private byte[] tokenCiphertext;
    @Column(name = "token_nonce", nullable = false, columnDefinition = "bytea")
    private byte[] tokenNonce;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public byte[] getRequestDigest() {
        return requestDigest;
    }

    public void setRequestDigest(byte[] value) {
        requestDigest = value;
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    public void setSubscriptionId(UUID value) {
        subscriptionId = value;
    }

    public byte[] getRequestFingerprint() {
        return requestFingerprint;
    }

    public void setRequestFingerprint(byte[] value) {
        requestFingerprint = value;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }

    public void setTokenVersion(int value) {
        tokenVersion = value;
    }

    public byte[] getTokenCiphertext() {
        return tokenCiphertext;
    }

    public void setTokenCiphertext(byte[] value) {
        tokenCiphertext = value;
    }

    public byte[] getTokenNonce() {
        return tokenNonce;
    }

    public void setTokenNonce(byte[] value) {
        tokenNonce = value;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant value) {
        createdAt = value;
    }
}
