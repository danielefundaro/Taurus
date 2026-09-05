package com.fundaro.zodiac.taurus.domain.notification;

import com.fundaro.zodiac.taurus.domain.inventory.TenantAuditedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "notification_category_preference")
public class NotificationCategoryPreference extends TenantAuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false)
    private NotificationProfile profile;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 32)
    private NotificationSource source;

    @Column(name = "in_app_enabled", nullable = false)
    private boolean inAppEnabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "push_mode", nullable = false, length = 20)
    private NotificationPushMode pushMode = NotificationPushMode.OFF;

    public NotificationProfile getProfile() { return profile; }
    public void setProfile(NotificationProfile profile) { this.profile = profile; }
    public NotificationSource getSource() { return source; }
    public void setSource(NotificationSource source) { this.source = source; }
    public boolean isInAppEnabled() { return inAppEnabled; }
    public void setInAppEnabled(boolean inAppEnabled) { this.inAppEnabled = inAppEnabled; }
    public NotificationPushMode getPushMode() { return pushMode; }
    public void setPushMode(NotificationPushMode pushMode) { this.pushMode = pushMode; }
}
