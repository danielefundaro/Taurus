import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { Router } from '@angular/router';
import { OperationalDashboard, OperationalItem } from '../../../../module/operational-dashboard';
import { OperationsWidgetComponent } from './operations-widget.component';

describe('OperationsWidgetComponent', () => {
    let fixture: ComponentFixture<OperationsWidgetComponent>;
    let component: OperationsWidgetComponent;
    let router: jasmine.SpyObj<Router>;

    const item: OperationalItem = {
        key: 'CALENDAR_AVAILABILITY_REQUIRED',
        type: 'CALENDAR_AVAILABILITY_REQUIRED',
        domain: 'CALENDAR',
        severity: 'WARNING',
        count: 1,
        title: 'Disponibilità da indicare',
        description: 'Un evento richiede una risposta.',
        dueAt: '2026-09-05T20:30:00+02:00',
        actionLabel: 'Rispondi',
        targetPath: '/calendar?attention=my-missing-availability'
    };

    const dashboard = (items: OperationalItem[] = [], status: OperationalDashboard['status'] = 'COMPLETE'): OperationalDashboard => ({
        generatedAt: '2026-09-04T10:15:30+02:00',
        status,
        summary: {
            groupCount: items.length,
            dangerCount: items.filter((value) => value.severity === 'DANGER').length,
            warningCount: items.filter((value) => value.severity === 'WARNING').length,
            infoCount: items.filter((value) => value.severity === 'INFO').length
        },
        items,
        unavailableDomains: status === 'PARTIAL' ? ['FINANCE'] : []
    });

    beforeEach(async () => {
        router = jasmine.createSpyObj<Router>('Router', ['navigateByUrl']);
        await TestBed.configureTestingModule({
            imports: [OperationsWidgetComponent],
            providers: [{ provide: Router, useValue: router }, provideNoopAnimations()]
        }).compileComponents();

        fixture = TestBed.createComponent(OperationsWidgetComponent);
        component = fixture.componentInstance;
    });

    it('shows three skeleton rows only during the initial load', () => {
        component.initialLoading = true;
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelectorAll('.operations-skeleton__row').length).toBe(3);
        expect(fixture.nativeElement.querySelector('[aria-busy="true"]')).not.toBeNull();
    });

    it('shows the positive empty state after a successful empty response', () => {
        component.dashboard = dashboard();
        fixture.detectChanges();

        expect(fixture.nativeElement.textContent).toContain('Tutto sotto controllo');
    });

    it('keeps available items visible for a partial response', () => {
        component.dashboard = dashboard([item], 'PARTIAL');
        fixture.detectChanges();

        expect(fixture.nativeElement.textContent).toContain('Alcune attività non sono disponibili');
        expect(fixture.nativeElement.textContent).toContain('Disponibilità da indicare');
        expect(fixture.nativeElement.textContent).toContain('1elemento');
    });

    it('emits a retry request after an initial error', () => {
        spyOn(component.refreshRequested, 'emit');
        component.error = true;
        fixture.detectChanges();

        const retry: HTMLButtonElement = fixture.nativeElement.querySelector('app-inline-alert button');
        retry.click();

        expect(component.refreshRequested.emit).toHaveBeenCalled();
    });

    it('navigates only to the path allowed for the operation type', () => {
        spyOn(console, 'warn');
        component['navigate'](item);
        component['navigate']({ ...item, targetPath: '//malicious.example' });

        expect(router.navigateByUrl).toHaveBeenCalledOnceWith('/calendar?attention=my-missing-availability');
    });

    it('accepts the event preparation target for its matching operation', () => {
        component['navigate']({
            ...item,
            key: 'EVENT_PREPARATION_BLOCKED',
            type: 'EVENT_PREPARATION_BLOCKED',
            targetPath: '/calendar?attention=event-preparation'
        });

        expect(router.navigateByUrl).toHaveBeenCalledOnceWith('/calendar?attention=event-preparation');
    });

    it('labels every severity with text, not only with colour', () => {
        component.dashboard = dashboard([
            { ...item, key: 'a', severity: 'DANGER' },
            { ...item, key: 'b', severity: 'WARNING', count: 3 },
            { ...item, key: 'c', severity: 'INFO', count: 0 }
        ]);
        fixture.detectChanges();

        const rows: HTMLElement[] = Array.from(fixture.nativeElement.querySelectorAll('.operations-list > li'));
        expect(rows.map((row) => row.querySelector('p-tag')?.textContent?.trim())).toEqual(['Scaduto o bloccante', 'Da gestire', 'Da verificare']);
        expect(rows.map((row) => ['danger', 'warning', 'info'].find((value) => row.querySelector('.operation-icon')?.classList.contains(`operation-icon--${value}`)))).toEqual([
            'danger',
            'warning',
            'info'
        ]);
        // Singolare solo per uno: tre e zero sono «elementi».
        expect(rows.map((row) => row.querySelector('.operation-count small')?.textContent)).toEqual(['elemento', 'elementi', 'elementi']);
        expect(rows[1].querySelector('.operation-count')?.getAttribute('aria-label')).toBe('Quantità 3');
    });

    it('keeps the last snapshot with a warning when a refresh fails', () => {
        component.dashboard = dashboard([item]);
        component.refreshWarning = true;
        component.error = true;
        fixture.detectChanges();

        expect(fixture.nativeElement.textContent).toContain('Aggiornamento non riuscito');
        expect(fixture.nativeElement.textContent).toContain('Disponibilità da indicare');
        expect(fixture.nativeElement.textContent).not.toContain('Attività non disponibili');
    });

    it('announces the outcome of a manual refresh to assistive technologies', () => {
        component.dashboard = dashboard([item]);
        component.announcement = 'Attività aggiornate.';
        fixture.detectChanges();

        const live: HTMLElement = fixture.nativeElement.querySelector('[aria-live="polite"]');
        expect(live.textContent?.trim()).toBe('Attività aggiornate.');
    });

    it('disables the refresh button while a request is running and exposes native buttons to the keyboard', () => {
        spyOn(component.refreshRequested, 'emit');
        component.dashboard = dashboard([item]);
        component.refreshing = true;
        fixture.detectChanges();

        const buttons: HTMLButtonElement[] = Array.from(fixture.nativeElement.querySelectorAll('button'));
        const refresh = buttons.find((button) => button.textContent?.includes('Aggiorna'))!;
        expect(refresh.disabled).toBeTrue();
        // L'azione della riga è un <button>: Invio e Spazio la attivano senza gestori dedicati.
        const action = buttons.find((button) => button.textContent?.includes('Rispondi'))!;
        expect(action.tagName).toBe('BUTTON');
        action.click();
        expect(router.navigateByUrl).toHaveBeenCalledOnceWith('/calendar?attention=my-missing-availability');

        fixture.componentRef.setInput('refreshing', false);
        fixture.detectChanges();
        refresh.click();
        expect(component.refreshRequested.emit).toHaveBeenCalledTimes(1);
    });

    it('opens the personal inventory, with the signatures box, for a required signature', () => {
        component['navigate']({
            ...item,
            key: 'INVENTORY_SIGNATURE_REQUIRED',
            type: 'INVENTORY_SIGNATURE_REQUIRED',
            domain: 'INVENTORY',
            targetPath: '/inventory?view=mine'
        });

        expect(router.navigateByUrl).toHaveBeenCalledOnceWith('/inventory?view=mine');
    });
});
