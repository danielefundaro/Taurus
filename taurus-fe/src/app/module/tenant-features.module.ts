export enum TenantFeature {
    FINANCE = 'FINANCE',
    INVENTORY = 'INVENTORY',
    ONBOARDING_IMPORT = 'ONBOARDING_IMPORT',
    EXTERNAL_CALENDAR_FEED = 'EXTERNAL_CALENDAR_FEED',
    INVENTORY_QR = 'INVENTORY_QR',
    NOTIFICATION_PREFERENCES = 'NOTIFICATION_PREFERENCES',
    WEB_PUSH_REMINDERS = 'WEB_PUSH_REMINDERS',
    EVENT_PREPARATION = 'EVENT_PREPARATION'
}

export interface TenantFeatures {
    tenantCode: string;
    tenantName: string;
    version: number;
    financeEnabled: boolean;
    inventoryEnabled: boolean;
    onboardingImportEnabled: boolean;
    externalCalendarFeedEnabled: boolean;
    inventoryQrEnabled: boolean;
    notificationPreferencesEnabled: boolean;
    webPushRemindersEnabled: boolean;
    eventPreparationEnabled: boolean;
}

export interface TenantFeatureCapability {
    available: boolean;
    reasonCode?: string;
    dependencies: TenantFeature[];
}

export type TenantFeatureCapabilities = Record<TenantFeature, TenantFeatureCapability>;
