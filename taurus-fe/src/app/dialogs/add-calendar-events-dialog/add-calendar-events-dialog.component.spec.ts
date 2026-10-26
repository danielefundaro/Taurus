import { signal } from '@angular/core';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { of } from 'rxjs';
import { CalendarEventDialogResult, CalendarEventSeriesPreview } from '../../module';
import { CalendarEventSeriesService, TenantFeatureService } from '../../service';
import { AddCalendarEventsDialogComponent } from './add-calendar-events-dialog.component';

describe('AddCalendarEventsDialogComponent', () => {
    function create(preview?: CalendarEventSeriesPreview): {
        component: AddCalendarEventsDialogComponent;
        close: jasmine.Spy;
    } {
        const close = jasmine.createSpy('close');
        const seriesService = {
            preview: () => of(preview ?? ({ timeZone: 'Europe/Rome', occurrenceCount: 1, occurrences: [], lastOccurrence: new Date() } as CalendarEventSeriesPreview))
        } as unknown as CalendarEventSeriesService;
        const featureService = { financeEnabled: signal(true) } as unknown as TenantFeatureService;
        const component = new AddCalendarEventsDialogComponent(
            { close } as unknown as DynamicDialogRef<AddCalendarEventsDialogComponent>,
            { data: { startDate: new Date(2026, 0, 31, 18, 0) } } as DynamicDialogConfig,
            seriesService,
            featureService
        );
        return { component, close };
    }

    it('says the rule in words, with the weekdays and the end chosen', () => {
        const { component } = create();
        component['recurring'] = true;
        component['frequency'] = 'WEEKLY';
        component['interval'] = 2;
        component['weekDays'] = ['MO', 'WE'];
        component['endType'] = 'COUNT';
        component['occurrenceCount'] = 8;

        expect(component['recurrenceSummary']).toBe('Ogni 2 settimane il Lun, Mer, alle 18:00, per 8 occorrenze.');
    });

    it('says a monthly rule with the day of the month and the end date', () => {
        const { component } = create();
        component['recurring'] = true;
        component['frequency'] = 'MONTHLY';
        component['interval'] = 1;
        component['endType'] = 'UNTIL';
        component['untilDate'] = new Date(2026, 11, 31);

        expect(component['recurrenceSummary']).toBe('Ogni mese il giorno 31, alle 18:00, fino al 31/12/2026.');
    });

    it('tells which months the rule skips and why', () => {
        const { component } = create({
            timeZone: 'Europe/Rome',
            occurrenceCount: 4,
            occurrences: [],
            lastOccurrence: new Date(),
            skippedMonths: ['2026-02', '2026-04']
        });
        component['recurring'] = true;
        component['frequency'] = 'MONTHLY';
        component['preview']();

        expect(component['skippedMonthsLabel']).toBe('2 mesi saltati perché il giorno 31 non esiste: febbraio 2026, aprile 2026.');
    });

    it('has no skipped months to report when the rule skips none', () => {
        const { component } = create();
        component['recurring'] = true;
        component['preview']();

        expect(component['skippedMonthsLabel']).toBe('');
    });

    it('sends the same idempotency key every time the series is submitted again', () => {
        const { component, close } = create();
        component['recurring'] = true;
        component['frequency'] = 'DAILY';
        component['event'].name = 'Prova';

        component['save']();
        component['save']();

        const [first, second] = close.calls.allArgs().map((args) => args[0] as CalendarEventDialogResult);
        expect(first.series?.idempotencyKey).toBeTruthy();
        expect(second.series?.idempotencyKey).toBe(first.series?.idempotencyKey);
    });
});
