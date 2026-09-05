export type CalendarFeedType = 'PERSONAL' | 'TENANT';
export type CalendarFeedScope = 'INTERNAL' | 'PUBLIC_ONLY';
export type CalendarFeedDetailLevel = 'MINIMAL' | 'STANDARD';
export type CalendarFeedStatus = 'ACTIVE' | 'REVOKED';

export interface CalendarFeed {
    id: string;
    name: string;
    feedType: CalendarFeedType;
    visibilityScope: CalendarFeedScope;
    detailLevel: CalendarFeedDetailLevel;
    pastDays: number;
    futureMonths: number;
    status: CalendarFeedStatus;
    tokenFingerprint: string;
    ownerUserId: number | null;
    createdBy: string;
    createdAt: string;
    lastAccessedAt: string | null;
}

export interface CalendarFeedCreate {
    name: string;
    visibilityScope?: CalendarFeedScope;
    detailLevel: CalendarFeedDetailLevel;
    pastDays: number;
    futureMonths: number;
    idempotencyKey: string;
}

export interface CalendarFeedSecret {
    id: string;
    name: string;
    feedType: CalendarFeedType;
    visibilityScope: CalendarFeedScope;
    detailLevel: CalendarFeedDetailLevel;
    pastDays: number;
    futureMonths: number;
    subscriptionUrl: string;
    tokenShownOnce: true;
    createdAt: string;
}
