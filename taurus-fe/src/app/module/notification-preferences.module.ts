export type NotificationSource = 'CALENDAR' | 'INVENTORY' | 'FINANCE' | 'CONTENT' | 'IDENTITY' | 'TENANT' | 'GENERAL';
export type NotificationPushMode = 'OFF' | 'IMMEDIATE' | 'DAILY_DIGEST';
export type NotificationPushPreview = 'PRIVATE' | 'FULL';

export interface NotificationCategoryPreference {
    source: NotificationSource;
    inAppEnabled: boolean;
    pushMode: NotificationPushMode;
}

export interface NotificationPreferences {
    version: number | null;
    timeZone: string;
    eventRemindersEnabled: boolean;
    defaultCalendarReminderMinutes: number;
    quietHours: {
        enabled: boolean;
        start: string;
        end: string;
    };
    pushPausedUntil: string | null;
    digestLocalTime: string;
    pushPreview: NotificationPushPreview;
    categories: NotificationCategoryPreference[];
}
