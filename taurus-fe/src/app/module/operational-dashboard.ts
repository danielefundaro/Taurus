export type DashboardResultStatus = 'COMPLETE' | 'PARTIAL';
export type DashboardDomain = 'LEGAL' | 'CALENDAR' | 'INVENTORY' | 'FINANCE' | 'NOTIFICATIONS';
export type DashboardSeverity = 'DANGER' | 'WARNING' | 'INFO';

export type DashboardOperationType =
    | 'LEGAL_ACCEPTANCE_REQUIRED'
    | 'CALENDAR_AVAILABILITY_REQUIRED'
    | 'CALENDAR_RESPONSES_MISSING'
    | 'INVENTORY_DECISION_REQUIRED'
    | 'INVENTORY_DECISIONS_PENDING'
    | 'INVENTORY_RETURNS_PENDING'
    | 'INVENTORY_ASSIGNMENTS_EXPIRING'
    | 'FINANCE_MOVEMENTS_UNRECONCILED'
    | 'NOTIFICATION_DELIVERY_FAILED';

export interface OperationalSummary {
    groupCount: number;
    dangerCount: number;
    warningCount: number;
    infoCount: number;
}

export interface OperationalItem {
    key: string;
    type: DashboardOperationType;
    domain: DashboardDomain;
    severity: DashboardSeverity;
    count: number;
    relatedCount?: number | null;
    title: string;
    description: string;
    dueAt?: string | null;
    actionLabel: string;
    targetPath: string;
}

export interface OperationalDashboard {
    generatedAt: string;
    status: DashboardResultStatus;
    summary: OperationalSummary;
    items: OperationalItem[];
    unavailableDomains: DashboardDomain[];
}

export type NotificationDeliveryStatus = 'PENDING' | 'DELIVERED' | 'FAILED' | 'SKIPPED';

export type NotificationDeliveryOrigin = 'OUTBOX' | 'PUSH' | 'REMINDER';

export interface NotificationDeliveryAdmin {
    rowKey: string;
    id: number;
    origin: NotificationDeliveryOrigin;
    source: string;
    operation?: string | null;
    deliveryType: string;
    status: NotificationDeliveryStatus;
    occurredAt: string;
    attempts: number;
    updatedAt: string;
    nextAttemptAt?: string | null;
    errorClass?: string | null;
    skipReason?: string | null;
    eventKeyHash: string;
}

export interface NotificationDeliveryFilters {
    origin?: NotificationDeliveryOrigin | null;
    source?: string | null;
    operation?: string | null;
    from?: string | null;
    to?: string | null;
}
