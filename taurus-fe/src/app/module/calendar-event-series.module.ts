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
    /** Chiave della creazione: una richiesta ripetuta dopo un timeout non crea una seconda serie. */
    idempotencyKey?: string;
    sourceOccurrenceId?: number;
    template: CalendarEvents;
    recurrence: RecurrenceRule;
}

export interface CalendarEventSeriesPreview {
    timeZone: string;
    occurrenceCount: number;
    occurrences: Date[];
    lastOccurrence: Date;
    /** Mesi saltati dalla regola, in forma `aaaa-mm`. */
    skippedMonths?: string[];
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
    /** Occorrenze lasciate come sono perché eccezioni. */
    keptExceptionCount?: number;
    /** Occorrenze ignorate perché già passate. */
    skippedPastCount?: number;
    /** Eccezioni che la nuova regola non genera più. */
    outsideRuleExceptionCount?: number;
    /** Vero quando la creazione ha ritrovato la serie di una richiesta già andata a buon fine. */
    replayed?: boolean;
}

export interface BulkAvailabilityResult {
    seriesId: number;
    affectedOccurrences: number;
}

export interface CalendarEventDialogResult {
    event?: CalendarEvents;
    series?: CalendarEventSeriesRequest;
}
