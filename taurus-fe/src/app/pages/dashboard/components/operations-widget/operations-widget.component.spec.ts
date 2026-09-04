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
});
