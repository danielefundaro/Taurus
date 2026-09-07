package com.fundaro.zodiac.taurus.domain.enumeration;

public enum TenantFeature {
    FINANCE("finance"),
    INVENTORY("inventory"),
    ONBOARDING_IMPORT("onboardingImport"),
    EXTERNAL_CALENDAR_FEED("externalCalendarFeed"),
    INVENTORY_QR("inventoryQr"),
    NOTIFICATION_PREFERENCES("notificationPreferences"),
    WEB_PUSH_REMINDERS("webPushReminders"),
    EVENT_PREPARATION("eventPreparation");

    private final String errorKey;

    TenantFeature(String errorKey) {
        this.errorKey = errorKey;
    }

    public String errorKey() {
        return errorKey;
    }
}
