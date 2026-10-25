package com.fundaro.zodiac.taurus.domain.enumeration;

import java.util.List;

public enum TenantFeature {
    FINANCE("finance"),
    INVENTORY("inventory"),
    ONBOARDING_IMPORT("onboardingImport"),
    EXTERNAL_CALENDAR_FEED("externalCalendarFeed"),
    INVENTORY_QR("inventoryQr"),
    NOTIFICATION_PREFERENCES("notificationPreferences"),
    WEB_PUSH_REMINDERS("webPushReminders"),
    EVENT_PREPARATION("eventPreparation"),
    SCORE_PLAYBACK("scorePlayback"),
    INVENTORY_LOAN_SHEET("inventoryLoanSheet"),
    INVENTORY_SIGNATURE("inventorySignature");

    private final String errorKey;

    TenantFeature(String errorKey) {
        this.errorKey = errorKey;
    }

    public String errorKey() {
        return errorKey;
    }

    /**
     * Le funzionalità da cui questa dipende: resta inefficace finché anche quelle non sono attive.
     * È l'unica definizione delle dipendenze, usata sia per lo stato effettivo sia per il log delle modifiche.
     */
    public List<TenantFeature> dependencies() {
        return switch (this) {
            case INVENTORY_QR, INVENTORY_LOAN_SHEET -> List.of(INVENTORY);
            case INVENTORY_SIGNATURE -> List.of(INVENTORY_LOAN_SHEET);
            case WEB_PUSH_REMINDERS -> List.of(NOTIFICATION_PREFERENCES);
            default -> List.of();
        };
    }
}
