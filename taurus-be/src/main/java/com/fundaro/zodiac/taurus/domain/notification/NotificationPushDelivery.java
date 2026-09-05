package com.fundaro.zodiac.taurus.domain.notification;

import com.fundaro.zodiac.taurus.domain.Notices;
import com.fundaro.zodiac.taurus.domain.inventory.TenantAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.ZonedDateTime;

@Entity
@Table(name = "notification_push_delivery")
public class NotificationPushDelivery extends TenantAuditedEntity {

    @Column(name = "source_event_key", nullable = false, length = 160)
    private String sourceEventKey;

    @Column(name = "user_id", nullable = false, length = 255)
    private String userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 32)
    private NotificationSource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_type", nullable = false, length = 16)
    private NotificationPushDeliveryType deliveryType;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "message", nullable = false, length = 255)
    private String message;

    @Column(name = "target_path", length = 500)
    private String targetPath;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notice_id")
    private Notices notice;

    @Column(name = "snooze_revision")
    private Integer snoozeRevision;

    @Column(name = "digest_local_date")
    private LocalDate digestLocalDate;

    @Column(name = "scheduled_at", nullable = false)
    private ZonedDateTime scheduledAt;

    @Column(name = "expires_at", nullable = false)
    private ZonedDateTime expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private NotificationStatus status = NotificationStatus.PENDING;

    @Column(name = "attempts", nullable = false)
    private int attempts;

    @Column(name = "next_attempt_at")
    private ZonedDateTime nextAttemptAt;

    @Column(name = "delivered_at")
    private ZonedDateTime deliveredAt;

    @Column(name = "skip_reason", length = 32)
    private String skipReason;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    public String getSourceEventKey() { return sourceEventKey; }
    public void setSourceEventKey(String value) { sourceEventKey = value; }
    public String getUserId() { return userId; }
    public void setUserId(String value) { userId = value; }
    public NotificationSource getSource() { return source; }
    public void setSource(NotificationSource value) { source = value; }
    public NotificationPushDeliveryType getDeliveryType() { return deliveryType; }
    public void setDeliveryType(NotificationPushDeliveryType value) { deliveryType = value; }
    public String getTitle() { return title; }
    public void setTitle(String value) { title = value; }
    public String getMessage() { return message; }
    public void setMessage(String value) { message = value; }
    public String getTargetPath() { return targetPath; }
    public void setTargetPath(String value) { targetPath = value; }
    public Notices getNotice() { return notice; }
    public void setNotice(Notices value) { notice = value; }
    public Integer getSnoozeRevision() { return snoozeRevision; }
    public void setSnoozeRevision(Integer value) { snoozeRevision = value; }
    public LocalDate getDigestLocalDate() { return digestLocalDate; }
    public void setDigestLocalDate(LocalDate value) { digestLocalDate = value; }
    public ZonedDateTime getScheduledAt() { return scheduledAt; }
    public void setScheduledAt(ZonedDateTime value) { scheduledAt = value; }
    public ZonedDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(ZonedDateTime value) { expiresAt = value; }
    public NotificationStatus getStatus() { return status; }
    public void setStatus(NotificationStatus value) { status = value; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int value) { attempts = value; }
    public ZonedDateTime getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(ZonedDateTime value) { nextAttemptAt = value; }
    public ZonedDateTime getDeliveredAt() { return deliveredAt; }
    public void setDeliveredAt(ZonedDateTime value) { deliveredAt = value; }
    public String getSkipReason() { return skipReason; }
    public void setSkipReason(String value) { skipReason = value; }
    public String getLastError() { return lastError; }
    public void setLastError(String value) { lastError = value; }
}
