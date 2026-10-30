package com.fundaro.zodiac.taurus.domain.calendarfeed;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * The history of a feed credential: who created, rotated, revoked or removed it, and when.
 *
 * <p>The audit fields of the subscription keep only the last change, so a rotation erases the
 * previous one and a revoked feed no longer says when it was created. This table keeps each step,
 * with the fingerprint that identifies the token in the operational logs and never the token.
 */
@Entity
@Table(name = "calendar_feed_audit")
public class CalendarFeedAuditEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;
    @Enumerated(EnumType.STRING)
    @Column(name = "feed_type", nullable = false, length = 16)
    private CalendarFeedType feedType;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private CalendarFeedAuditAction action;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private CalendarFeedAuditSource source;
    @Column(name = "token_version", nullable = false)
    private int tokenVersion;
    @Column(name = "token_fingerprint", length = 12)
    private String tokenFingerprint;
    @Column(nullable = false, length = 255)
    private String actor;
    @Column(length = 255)
    private String reason;
    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    public Long getId() {
        return id;
    }

    public UUID getSubscriptionId() {
        return subscriptionId;
    }

    public void setSubscriptionId(UUID value) {
        subscriptionId = value;
    }

    public CalendarFeedType getFeedType() {
        return feedType;
    }

    public void setFeedType(CalendarFeedType value) {
        feedType = value;
    }

    public CalendarFeedAuditAction getAction() {
        return action;
    }

    public void setAction(CalendarFeedAuditAction value) {
        action = value;
    }

    public CalendarFeedAuditSource getSource() {
        return source;
    }

    public void setSource(CalendarFeedAuditSource value) {
        source = value;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }

    public void setTokenVersion(int value) {
        tokenVersion = value;
    }

    public String getTokenFingerprint() {
        return tokenFingerprint;
    }

    public void setTokenFingerprint(String value) {
        tokenFingerprint = value;
    }

    public String getActor() {
        return actor;
    }

    public void setActor(String value) {
        actor = value;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String value) {
        reason = value;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant value) {
        occurredAt = value;
    }
}
