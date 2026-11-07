import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { NavigationEnd, Router } from '@angular/router';
import { of, Subject } from 'rxjs';
import { environment } from '../../environments/environment';
import { RoleEnums } from '../constants';
import { GuidedTour, GuidedTourProgress } from '../module';
import { HANDLED_ERRORS } from './api-error-context';
import { GuidedTourService } from './guided-tour.service';
import { KeycloakService } from './keycloak.service';
import { TenantFeatureService } from './tenant-feature.service';
import { TenantStatusService } from './tenant-status.service';
import { ToastService } from './toast.service';

const progress = (overrides: Partial<GuidedTourProgress> = {}): GuidedTourProgress => ({
    status: 'IN_PROGRESS',
    seenSteps: ['common.welcome'],
    lastStepId: 'common.welcome',
    autoProposals: 0,
    catalogRevision: 1,
    startedAt: '2026-11-04T10:00:00+01:00',
    completedAt: null,
    version: 0,
    ...overrides
});

const tour = (overrides: Partial<GuidedTour> = {}): GuidedTour => ({
    enabled: true,
    settings: { autoStartEnabled: true, legacyInvitation: false, version: null },
    role: RoleEnums.USER,
    progress: null,
    ...overrides
});

describe('GuidedTourService', () => {
    const url = `${environment.baseUrl}/guided-tour`;
    let service: GuidedTourService;
    let http: HttpTestingController;
    let router: { url: string; events: Subject<unknown> };
    let toast: jasmine.SpyObj<ToastService>;
    let provisioned: boolean;
    let featuresLoaded: boolean;

    /** The service stays closed while a mask is in the document, whichever spec left it there. */
    function removeMasks(): void {
        document.querySelectorAll('.p-dialog-mask, .p-overlay-mask').forEach((mask) => mask.remove());
    }

    beforeEach(() => {
        sessionStorage.clear();
        removeMasks();
        provisioned = true;
        featuresLoaded = true;
        router = { url: '/', events: new Subject() };
        toast = jasmine.createSpyObj<ToastService>('ToastService', ['info', 'warn', 'success']);
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: Router, useValue: router },
                { provide: KeycloakService, useValue: { currentUserId: 'user-1', currentUserTenantCode: 'banda', currentUserRole: RoleEnums.USER } },
                { provide: TenantStatusService, useValue: { load: () => of({ tenantCode: 'banda', provisioned }) } },
                { provide: TenantFeatureService, useValue: { loaded: () => featuresLoaded, isEnabled: () => true } },
                { provide: ToastService, useValue: toast }
            ]
        });
        service = TestBed.inject(GuidedTourService);
        http = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        http.verify();
        sessionStorage.clear();
        removeMasks();
    });

    /** Lets the dashboard propose the tour and answers the first read with the given state. */
    function propose(state: GuidedTour): void {
        service.proposeFromDashboard();
        tick(GuidedTourService.AUTO_START_DELAY_MS);
        http.expectOne(url).flush(state);
    }

    it('reads the state silently, once, and never in bootstrap mode', () => {
        let received: GuidedTour | null | undefined;
        service.load().subscribe((value) => (received = value));
        service.load().subscribe();
        const request = http.expectOne(url);
        expect(request.request.context.get(HANDLED_ERRORS)).toEqual(['*']);
        request.flush(tour());

        expect(received?.role).toBe(RoleEnums.USER);
        service.load().subscribe();
        http.expectNone(url);
    });

    it('does not call the tenant API before the tenant is provisioned', () => {
        provisioned = false;
        let received: GuidedTour | null | undefined;
        service.load().subscribe((value) => (received = value));

        http.expectNone(url);
        expect(received).toBeNull();
    });

    it('opens the welcome by itself on the dashboard, after the first rendering', fakeAsync(() => {
        service.proposeFromDashboard();
        tick(GuidedTourService.AUTO_START_DELAY_MS - 1);
        http.expectNone(url);
        tick(1);
        http.expectOne(url).flush(tour());

        expect(service.session()).toEqual(jasmine.objectContaining({ presentation: 'tour', automatic: true }));
    }));

    it('stays closed on other pages and while a dialog is open', fakeAsync(() => {
        router.url = '/calendar?view=list';
        propose(tour());
        expect(service.session()).toBeNull();

        router.url = '/';
        const mask = document.createElement('div');
        mask.className = 'p-overlay-mask';
        document.body.appendChild(mask);
        service.proposeFromDashboard();
        tick(GuidedTourService.AUTO_START_DELAY_MS);
        expect(service.session()).toBeNull();
    }));

    it('respects the personal switch', fakeAsync(() => {
        propose(tour({ settings: { autoStartEnabled: false, legacyInvitation: false, version: 1 } }));
        expect(service.session()).toBeNull();
    }));

    it('needs the application switch', fakeAsync(() => {
        propose(tour({ enabled: false }));
        expect(service.session()).toBeNull();
    }));

    it('waits for the features of the tenant', fakeAsync(() => {
        featuresLoaded = false;
        propose(tour());
        expect(service.session()).toBeNull();
    }));

    it('stops proposing a tour closed three times, and lights the help button instead', fakeAsync(() => {
        propose(tour({ progress: progress({ autoProposals: 2 }) }));
        expect(service.session()?.presentation).toBe('tour');
        expect(service.attention()).toBeFalse();

        service.pause();
        const pause = http.expectOne(`${url}/progress`);
        expect(pause.request.body.autoProposals).toBe(3);
        pause.flush(tour({ progress: progress({ autoProposals: 3, version: 1 }) }));

        service.proposeFromDashboard();
        tick(GuidedTourService.AUTO_START_DELAY_MS);
        expect(service.session()).toBeNull();
        expect(service.attention()).toBeTrue();

        service.open();
        expect(service.attention()).toBeFalse();
        expect(service.session()).toEqual(jasmine.objectContaining({ presentation: 'tour', automatic: false }));
    }));

    it('never proposes a concluded or skipped tour', fakeAsync(() => {
        propose(tour({ progress: progress({ status: 'COMPLETED', completedAt: '2026-11-04T11:00:00+01:00' }) }));
        expect(service.session()).toBeNull();
    }));

    it('postpones the tour until the end of the browser session', fakeAsync(() => {
        propose(tour());
        service.postpone();
        expect(service.session()).toBeNull();

        service.proposeFromDashboard();
        tick(GuidedTourService.AUTO_START_DELAY_MS);
        expect(service.session()).toBeNull();
        expect(sessionStorage.getItem('guidedTour.postponed.user-1.banda.ROLE_USER')).toBe('true');
    }));

    it('invites the members present at the release instead of opening the tour', fakeAsync(() => {
        propose(tour({ settings: { autoStartEnabled: true, legacyInvitation: true, version: 0 } }));
        expect(service.session()).toEqual(jasmine.objectContaining({ presentation: 'invitation', automatic: false }));

        router.url = '/calendar';
        router.events.next(new NavigationEnd(1, '/calendar', '/calendar'));
        expect(service.session()).toBeNull();
    }));

    it('skips the role when the invitation is declined', fakeAsync(() => {
        propose(tour({ settings: { autoStartEnabled: true, legacyInvitation: true, version: 0 } }));
        service.useCatalogRevision(1);
        service.declineInvitation();

        const request = http.expectOne(`${url}/progress`);
        expect(request.request.method).toBe('PUT');
        expect(request.request.body).toEqual({ status: 'SKIPPED', seenSteps: [], lastStepId: null, autoProposals: 0, catalogRevision: 1, version: null });
        request.flush(tour({ progress: progress({ status: 'SKIPPED', seenSteps: [], version: 0 }) }));
        expect(service.session()).toBeNull();
    }));

    it('counts a proposal only for a tour opened by itself, and writes nothing before the tour has a state', fakeAsync(() => {
        service.skip();
        http.expectNone(`${url}/progress`);

        service.open();
        http.expectOne(url).flush(tour({ progress: progress({ autoProposals: 1 }) }));
        service.pause();
        const pause = http.expectOne(`${url}/progress`);
        expect(pause.request.body).toEqual(jasmine.objectContaining({ autoProposals: 1, lastStepId: 'common.welcome' }));
        pause.flush(tour({ progress: progress({ autoProposals: 1, version: 1 }) }));

        service.restart().subscribe();
        http.expectOne(`${url}/progress`).flush(tour());
        service.open();
        service.pause();
        http.expectNone(`${url}/progress`);
    }));

    it('sends the steps taken while a write is travelling as soon as it returns', fakeAsync(() => {
        propose(tour({ progress: progress() }));
        service.visit('common.navigation');
        tick(GuidedTourService.SAVE_DELAY_MS);
        const first = http.expectOne(`${url}/progress`);

        service.complete('common.help');
        http.expectNone(`${url}/progress`);
        first.flush(tour({ progress: progress({ seenSteps: ['common.welcome', 'common.navigation'], version: 1 }) }));

        expect(service.tour()?.progress?.status).toBe('COMPLETED');
        const second = http.expectOne(`${url}/progress`);
        expect(second.request.body).toEqual(jasmine.objectContaining({ status: 'COMPLETED', lastStepId: 'common.help', version: 1 }));
        second.flush(tour({ progress: progress({ status: 'COMPLETED', version: 2 }) }));
    }));

    it('gathers the steps of a second into one write', fakeAsync(() => {
        propose(tour());
        service.useCatalogRevision(1);
        service.begin('common.welcome');
        http.expectOne(`${url}/progress`).flush(tour({ progress: progress({ version: 0 }) }));

        service.visit('common.navigation');
        tick(500);
        service.visit('calendar.availability');
        tick(GuidedTourService.SAVE_DELAY_MS - 1);
        http.expectNone(`${url}/progress`);
        tick(1);

        const request = http.expectOne(`${url}/progress`);
        expect(request.request.body).toEqual(jasmine.objectContaining({ seenSteps: ['common.welcome', 'common.navigation', 'calendar.availability'], lastStepId: 'calendar.availability', version: 0 }));
        request.flush(tour({ progress: progress({ seenSteps: ['common.welcome', 'common.navigation', 'calendar.availability'], version: 1 }) }));
    }));

    it('saves at once when the tour is skipped or concluded', fakeAsync(() => {
        propose(tour({ progress: progress() }));
        service.visit('common.navigation');
        service.complete('common.help');

        const request = http.expectOne(`${url}/progress`);
        expect(request.request.body).toEqual(jasmine.objectContaining({ status: 'COMPLETED', lastStepId: 'common.help', seenSteps: ['common.welcome', 'common.navigation', 'common.help'] }));
        request.flush(tour({ progress: progress({ status: 'COMPLETED', version: 1 }) }));
        expect(service.session()).toBeNull();
        expect(toast.info).not.toHaveBeenCalled();

        service.open();
        service.skip();
        http.expectOne(`${url}/progress`).flush(tour({ progress: progress({ status: 'SKIPPED', version: 2 }) }));
        expect(toast.info).toHaveBeenCalledWith('Guida chiusa', 'Puoi riaprirla dal pulsante ? in alto.');
    }));

    it('merges the seen steps after a version conflict and sends once more, silently', fakeAsync(() => {
        propose(tour({ progress: progress({ version: 3 }) }));
        service.visit('calendar.availability');
        tick(GuidedTourService.SAVE_DELAY_MS);
        http.expectOne(`${url}/progress`).flush({ message: 'error.guidedTour.versionConflict' }, { status: 409, statusText: 'Conflict' });

        http.expectOne(url).flush(tour({ progress: progress({ seenSteps: ['common.welcome', 'catalog.tracks'], version: 4 }) }));
        const retry = http.expectOne(`${url}/progress`);
        expect(retry.request.body).toEqual(jasmine.objectContaining({ seenSteps: ['common.welcome', 'catalog.tracks', 'calendar.availability'], version: 4 }));
        retry.flush(tour({ progress: progress({ seenSteps: ['common.welcome', 'catalog.tracks', 'calendar.availability'], version: 5 }) }));
        expect(toast.warn).not.toHaveBeenCalled();
    }));

    it('keeps the tour going after a network error and saves again at the next step', fakeAsync(() => {
        propose(tour({ progress: progress() }));
        service.visit('common.navigation');
        tick(GuidedTourService.SAVE_DELAY_MS);
        http.expectOne(`${url}/progress`).error(new ProgressEvent('error'), { status: 0 });

        service.visit('calendar.availability');
        tick(GuidedTourService.SAVE_DELAY_MS);
        const request = http.expectOne(`${url}/progress`);
        expect(request.request.body.seenSteps).toEqual(['common.welcome', 'common.navigation', 'calendar.availability']);
        request.flush(tour({ progress: progress({ version: 1 }) }));
    }));

    it('leaves the application usable when the state cannot be read', fakeAsync(() => {
        service.proposeFromDashboard();
        tick(GuidedTourService.AUTO_START_DELAY_MS);
        http.expectOne(url).error(new ProgressEvent('error'), { status: 0 });
        expect(service.session()).toBeNull();

        service.open();
        http.expectOne(url).error(new ProgressEvent('error'), { status: 0 });
        expect(toast.warn).toHaveBeenCalledWith('Guida non disponibile', 'Riprova tra qualche istante.');
    }));

    it('switches the automatic start off from the welcome and says where to switch it on', fakeAsync(() => {
        propose(tour({ settings: { autoStartEnabled: true, legacyInvitation: false, version: 2 } }));
        service.disableAutoStart();

        const request = http.expectOne(`${url}/settings`);
        expect(request.request.body).toEqual({ autoStartEnabled: false, version: 2 });
        request.flush(tour({ settings: { autoStartEnabled: false, legacyInvitation: false, version: 3 } }));
        expect(service.session()).toBeNull();
        expect(service.tour()?.settings.autoStartEnabled).toBeFalse();
        expect(toast.info).toHaveBeenCalledWith('Avvio automatico disattivato', 'Puoi riattivarlo dal profilo, nella sezione Guida.');
    }));

    it('restarts the role from the profile and forgets the pending steps', fakeAsync(() => {
        propose(tour({ progress: progress({ version: 2 }) }));
        service.visit('common.navigation');
        service.restart().subscribe();

        const request = http.expectOne(`${url}/progress`);
        expect(request.request.method).toBe('DELETE');
        request.flush(tour());
        tick(GuidedTourService.SAVE_DELAY_MS);
        http.expectNone(`${url}/progress`);
        expect(service.tour()?.progress).toBeNull();
    }));

    describe('page guide', () => {
        it('is offered only on a page with steps for the role and the features', async () => {
            router.url = '/profile?tab=1';
            expect(await service.pageGuideAvailable()).toBeTrue();
            router.url = '/finance';
            expect(await service.pageGuideAvailable()).toBeFalse();
            router.url = '/notfound';
            expect(await service.pageGuideAvailable()).toBeFalse();
        });

        it('opens without writing on the server and closes when the page changes', async () => {
            router.url = '/profile';
            await service.openPage();
            expect(service.session()?.presentation).toBe('page');
            expect(service.session()?.steps?.[0].id).toBe('page.profile.personal');

            service.open();
            expect(service.session()?.presentation).toBe('page');

            router.url = '/';
            router.events.next(new NavigationEnd(1, '/', '/'));
            expect(service.session()).toBeNull();
        });

        it('says when the page has no guide', async () => {
            router.url = '/notfound';
            await service.openPage();
            expect(service.session()).toBeNull();
            expect(toast.info).toHaveBeenCalled();
        });

        it('stays closed with the switch of the application off', async () => {
            service.load().subscribe();
            http.expectOne(url).flush(tour({ enabled: false }));
            router.url = '/profile';
            expect(await service.pageGuideAvailable()).toBeFalse();
        });
    });
});
