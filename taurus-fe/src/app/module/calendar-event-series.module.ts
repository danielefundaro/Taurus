import { CalendarEvents } from './calendar-events.module';

export type RecurrenceFrequency = 'DAILY' | 'WEEKLY' | 'MONTHLY' | 'YEARLY';
export type RecurrenceEndType = 'COUNT' | 'UNTIL';
export type RecurrenceWeekDay = 'MO' | 'TU' | 'WE' | 'TH' | 'FR' | 'SA' | 'SU';

export interface RecurrenceEnd {
    type: RecurrenceEndType;
    count?: number;
    until?: string;
}

export interface RecurrenceRule {
    frequency: RecurrenceFrequency;
    interval: number;
    weekDays: RecurrenceWeekDay[];
    end: RecurrenceEnd;
}

export interface CalendarEventSeriesRequest {
    entityVersion?: number;
    sourceOccurrenceId?: number;
    template: CalendarEvents;
    recurrence: RecurrenceRule;
}

export interface CalendarEventSeriesPreview {
    timeZone: string;
    occurrenceCount: number;
    occurrences: Date[];
    lastOccurrence: Date;
}

export interface CalendarEventSeries {
    id: number;
    entityVersion: number;
    timeZone: string;
    template: CalendarEvents;
    recurrence: RecurrenceRule;
    occurrenceCount: number;
    exceptionCount: number;
    createdCount?: number;
    updatedCount?: number;
    deletedCount?: number;
}

export interface BulkAvailabilityResult {
    seriesId: number;
    affectedOccurrences: number;
}

export interface CalendarEventDialogResult {
    event?: CalendarEvents;
    series?: CalendarEventSeriesRequest;
}
