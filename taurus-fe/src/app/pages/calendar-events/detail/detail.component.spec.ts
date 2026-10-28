import { registerLocaleData } from '@angular/common';
import { HttpRequest, provideHttpClient } from '@angular/common/http';
import localeIt from '@angular/common/locales/it';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { fakeAsync, flushMicrotasks, TestBed, tick } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { ConfirmationService } from 'primeng/api';
import { RoleEnums } from '../../../constants';
import { EventPreparationView } from '../../../module';
import { DateConverterPipe } from '../../../pipe';
import { KeycloakService, TenantFeatureService, ToastService } from '../../../service';
import { DetailComponent } from './detail.component';
import { PreparationProgramComponent } from './preparation/preparation-program.component';

const evaluation = {
    evaluatedAt: '2026-10-27T10:00:00+01:00',
    phase: 'FOLLOW_UP',
    preparationStatus: 'ATTENTION',
    closureStatus: 'TO_CLOSE',
    completionPercent: 50,
    passedChecks: 1,
    applicableChecks: 2,
    blockerCount: 0,
    warningCount: 1,
    infoCount: 0,
    issues: [{ code: 'MATERIAL_NOT_READY', area: 'MATERIALS', severity: 'WARNING', message: 'Assegna e conferma i materiali', action: '#preparation-materials', details: [], detailCount: 0 }],
    issueCount: 1,
    excludedAreas: []
};

const configuration = {
    profile: 'PERFORMANCE',
    locationRequired: true,
    programRequired: true,
    scoresRequired: true,
    availabilityRequired: true,
    minimumAvailableParticipants: 3,
    availabilityDeadlineMinutes: 1440,
    materialsRequired: true,
    materialsDeadlineMinutes: 180,
    budgetRequired: true,
    presenceClosureRequired: true,
    financialClosureRequired: true,
    version: 2
};

const availability = { expected: 4, available: 2, unavailable: 1, missing: 1, minimumRequired: 3 };
const program = [{ id: 1, trackId: 7, trackName: 'Marcia', trackState: 'PUBLIC', trackAvailable: true, encore: false, order: 0, scores: [] }];

const views: Record<string, EventPreparationView> = {
    admin: { configuration, evaluation, program, programVersion: 3, availability, materials: [], materialsVersion: 1, confirmations: { budgetCurrent: false, presenceCurrent: false, noMovementsCurrent: false } } as unknown as EventPreparationView,
    treasurer: {
        configuration: { ...configuration, programRequired: false, scoresRequired: false, availabilityRequired: false, materialsRequired: false, presenceClosureRequired: false, locationRequired: false },
        evaluation,
        program: [],
        availability: { expected: 0, available: 0, unavailable: 0, missing: 0 },
        materials: [],
        confirmations: { budgetCurrent: false, presenceCurrent: false, noMovementsCurrent: false }
    } as unknown as EventPreparationView,
    personal: {
        configuration: { ...configuration, materialsRequired: false, budgetRequired: false, financialClosureRequired: false, presenceClosureRequired: false },
        program,
        availability,
        materials: []
    } as unknown as EventPreparationView
};

registerLocaleData(localeIt);

describe('DetailComponent preparation', () => {
    let http: HttpTestingController;
    let requested: string[];

    function setUp(role: RoleEnums): void {
        TestBed.configureTestingModule({
            providers: [
                provideRouter([{ path: 'calendar/:id', component: DetailComponent }]),
                provideHttpClient(),
                provideHttpClientTesting(),
                provideNoopAnimations(),
                ConfirmationService,
                DateConverterPipe,
                {
                    provide: KeycloakService,
                    useValue: {
                        currentUserRole: role,
                        isAdmin: [RoleEnums.ADMIN, RoleEnums.SUPER_ADMIN].includes(role),
                        isUser: role === RoleEnums.USER,
                        isUserExternal: role === RoleEnums.USER_EXTERNAL
                    }
                },
                { provide: ToastService, useValue: jasmine.createSpyObj<ToastService>('ToastService', ['success', 'info', 'warn', 'error']) },
                {
                    provide: TenantFeatureService,
                    useValue: { financeEnabled: signal(true), inventoryEnabled: signal(true), eventPreparationEnabled: signal(true), webPushRemindersEnabled: signal(false) }
                }
            ]
        });
        http = TestBed.inject(HttpTestingController);
        requested = [];
    }

    function respond(request: HttpRequest<unknown>): object | null {
        const path = new URL(request.url).pathname.replace('/api', '');
        if (path === '/users/me') return { id: 1 };
        if (path.endsWith('/calendar-events/42/preparation') && path.startsWith('/calendar-events')) return views['admin'];
        if (path.endsWith('/calendar-events/42/preparation/catalogue')) return views['admin'];
        if (path === '/finance/events/42/preparation') return views['treasurer'];
        if (path.endsWith('/calendar-events/42/preparation')) return views['personal'];
        if (path === '/finance/events/42') return { eventId: 42, eventName: 'Concerto', expectedFee: 100, expectedCostItems: [], received: 0, paid: 0, remainingIncome: 100, remainingExpense: 0 };
        if (path.endsWith('/calendar-events/42')) return { id: 42, name: 'Concerto', startDate: '2026-10-20T20:00:00+02:00', availableUsers: [], unavailableUsers: [], presentUsers: [] };
        if (path === '/users') return { content: [], totalElements: 0 };
        return null;
    }

    function serve(harness: RouterTestingHarness, failPreparation = false): void {
        for (let round = 0; round < 10; round++) {
            const pending = http.match(() => true);
            if (!pending.length) return;
            pending.forEach((request) => {
                requested.push(`${request.request.method} ${new URL(request.request.url).pathname.replace('/api', '')}`);
                if (failPreparation && request.request.url.endsWith('/preparation')) request.flush({ message: 'boom' }, { status: 500, statusText: 'Error' });
                else request.flush(respond(request.request));
            });
            harness.detectChanges();
        }
    }

    async function open(role: RoleEnums, url = '/calendar/42'): Promise<{ harness: RouterTestingHarness; component: DetailComponent; element: HTMLElement }> {
        setUp(role);
        const harness = await RouterTestingHarness.create();
        const component = await harness.navigateByUrl(url, DetailComponent);
        serve(harness);
        return { harness, component, element: harness.routeNativeElement as HTMLElement };
    }

    const sections = (element: HTMLElement) => Array.from(element.querySelectorAll('app-detail-section[id]')).map((section) => section.id);

    it('gives administrators every area of the preparation and the anchors of its issues', async () => {
        const { element } = await open(RoleEnums.ADMIN);

        expect(sections(element)).toEqual(['preparation', 'preparation-confirmations', 'preparation-program', 'preparation-materials', 'event-data', 'preparation-finance', 'availability', 'presence']);
        expect(element.querySelector('app-preparation-summary')).not.toBeNull();
        expect(element.querySelector('app-preparation-configuration')).not.toBeNull();
        expect(element.querySelectorAll('app-preparation-confirmation').length).toBe(3);
        expect(requested).toContain('GET /calendar-events/42/preparation');
    });

    it('keeps the treasurer on the finance API and on the economic areas', async () => {
        const { element } = await open(RoleEnums.TREASURER);

        expect(requested.filter((request) => request !== 'GET /users/me').every((request) => request.startsWith('GET /finance/'))).toBeTrue();
        expect(sections(element)).toEqual(['preparation', 'preparation-confirmations', 'preparation-finance']);
        expect(element.querySelector('app-preparation-configuration')).toBeNull();
        expect(element.querySelector('#preparation-finance app-preparation-confirmation')).not.toBeNull();
    });

    it('shows members the program to read and no state of the preparation', async () => {
        const { element } = await open(RoleEnums.USER);

        expect(requested).toContain('GET /user/calendar-events/42/preparation');
        expect(sections(element)).toEqual(['preparation-program', 'event-data', 'availability']);
        expect(element.querySelector('app-preparation-summary')).toBeNull();
        expect(element.querySelector('#preparation-program p-autocomplete')).toBeNull();
    });

    it('lists every unit with changes in the exit guard and keeps it through a reload after a conflict', async () => {
        const { harness, component } = await open(RoleEnums.ADMIN);
        const programComponent = harness.routeDebugElement!.query(By.directive(PreparationProgramComponent)).componentInstance as PreparationProgramComponent;

        programComponent.dirtyChange.emit(true);
        expect(component.dirtyUnitLabels).toEqual(['programma']);

        component['reloadPreparation']();
        serve(harness);
        expect(requested.filter((request) => request === 'GET /calendar-events/42/preparation').length).toBe(2);
        expect(component.dirtyUnitLabels).toEqual(['programma']);
        expect(component['preparationAnnouncement']).toBe('Preparazione ricaricata con i dati aggiornati.');
    });

    it('announces a save in the polite live region', async () => {
        const { harness, component, element } = await open(RoleEnums.ADMIN);

        component['onPreparationChanged'](views['admin'], 'Programma aggiornato', 'La prontezza è stata ricalcolata.');
        harness.detectChanges();

        expect(element.querySelector('[aria-live="polite"].sr-only')!.textContent).toContain('Programma aggiornato. La prontezza è stata ricalcolata.');
    });

    it('opens the section named by the link of a notification', fakeAsync(() => {
        let element!: HTMLElement;
        open(RoleEnums.ADMIN, '/calendar/42#preparation-materials').then((opened) => (element = opened.harness.fixture.nativeElement));
        flushMicrotasks();
        document.body.appendChild(element);
        tick();

        expect(document.activeElement?.id).toBe('preparation-materials');
        element.remove();
    }));

    it('offers to retry a preparation that could not be loaded', async () => {
        setUp(RoleEnums.ADMIN);
        const harness = await RouterTestingHarness.create();
        await harness.navigateByUrl('/calendar/42', DetailComponent);
        serve(harness, true);

        const alert = harness.routeNativeElement!.querySelector('#preparation app-inline-alert');
        expect(alert?.textContent).toContain('Preparazione non disponibile');
    });
});
