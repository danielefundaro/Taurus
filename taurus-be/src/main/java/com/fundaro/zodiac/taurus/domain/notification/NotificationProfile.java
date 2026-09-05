package com.fundaro.zodiac.taurus.domain.notification;

import com.fundaro.zodiac.taurus.domain.Users;
import com.fundaro.zodiac.taurus.domain.inventory.TenantAuditedEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "notification_profile")
public class NotificationProfile extends TenantAuditedEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;

    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone;

    @Column(name = "event_reminders_enabled", nullable = false)
    private boolean eventRemindersEnabled = true;

    @Column(name = "default_calendar_reminder_minutes", nullable = false)
    private int defaultCalendarReminderMinutes = 30;

    @Column(name = "quiet_hours_enabled", nullable = false)
    private boolean quietHoursEnabled;

    @Column(name = "quiet_start", nullable = false)
    private LocalTime quietStart = LocalTime.of(22, 0);

    @Column(name = "quiet_end", nullable = false)
    private LocalTime quietEnd = LocalTime.of(7, 0);

    @Column(name = "push_paused_until")
    private ZonedDateTime pushPausedUntil;

    @Column(name = "digest_local_time", nullable = false)
    private LocalTime digestLocalTime = LocalTime.of(8, 0);

    @Enumerated(EnumType.STRING)
    @Column(name = "push_preview", nullable = false, length = 16)
    private NotificationPushPreview pushPreview = NotificationPushPreview.PRIVATE;

    @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("source")
    private List<NotificationCategoryPreference> categories = new ArrayList<>();

    public void replaceCategories(List<NotificationCategoryPreference> values) {
        categories.clear();
        values.forEach(value -> {
            value.setProfile(this);
            categories.add(value);
        });
    }

    public Users getUser() { return user; }
    public void setUser(Users user) { this.user = user; }
    public String getTimeZone() { return timeZone; }
    public void setTimeZone(String timeZone) { this.timeZone = timeZone; }
    public boolean isEventRemindersEnabled() { return eventRemindersEnabled; }
    public void setEventRemindersEnabled(boolean eventRemindersEnabled) { this.eventRemindersEnabled = eventRemindersEnabled; }
    public int getDefaultCalendarReminderMinutes() { return defaultCalendarReminderMinutes; }
    public void setDefaultCalendarReminderMinutes(int value) { this.defaultCalendarReminderMinutes = value; }
    public boolean isQuietHoursEnabled() { return quietHoursEnabled; }
    public void setQuietHoursEnabled(boolean quietHoursEnabled) { this.quietHoursEnabled = quietHoursEnabled; }
    public LocalTime getQuietStart() { return quietStart; }
    public void setQuietStart(LocalTime quietStart) { this.quietStart = quietStart; }
    public LocalTime getQuietEnd() { return quietEnd; }
    public void setQuietEnd(LocalTime quietEnd) { this.quietEnd = quietEnd; }
    public ZonedDateTime getPushPausedUntil() { return pushPausedUntil; }
    public void setPushPausedUntil(ZonedDateTime pushPausedUntil) { this.pushPausedUntil = pushPausedUntil; }
    public LocalTime getDigestLocalTime() { return digestLocalTime; }
    public void setDigestLocalTime(LocalTime digestLocalTime) { this.digestLocalTime = digestLocalTime; }
    public NotificationPushPreview getPushPreview() { return pushPreview; }
    public void setPushPreview(NotificationPushPreview pushPreview) { this.pushPreview = pushPreview; }
    public List<NotificationCategoryPreference> getCategories() { return categories; }
}
