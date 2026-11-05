import { signal } from '@angular/core';
import { ComponentFixture, fakeAsync, TestBed, tick } from '@angular/core/testing';
import { provideNoopAnimations } from '@angular/platform-browser/animations';
import { Router } from '@angular/router';
import { RoleEnums } from '../../constants';
import { GUIDED_TOUR_CATALOG_REVISION } from '../../constants/guided-tour-steps';
import { GuidedTour, GuidedTourStep, TenantFeature } from '../../module';
import { GuidedTourService, GuidedTourSession, KeycloakService, LayoutService, TenantFeatureService } from '../../service';
import { GuidedTourComponent, place } from './guided-tour.component';

describe('GuidedTourComponent', () => {
    let fixture: ComponentFixture<GuidedTourComponent>;
    let session: ReturnType<typeof signal<GuidedTourSession | null>>;
    let state: ReturnType<typeof signal<GuidedTour | null>>;
    let service: jasmine.SpyObj<GuidedTourService>;
    let router: { url: string; navigateByUrl: jasmine.Spy };
    let mobile: boolean;
    let anchors: HTMLElement[];

    const tour = (overrides: Partial<GuidedTour> = {}): GuidedTour => ({
        enabled: true,
        settings: { autoStartEnabled: true, legacyInvitation: false, version: 0 },
        role: RoleEnums.USER,
        progress: null,
        ...overrides
    });

    beforeEach(() => {
        mobile = false;
        anchors = [];
        session = signal(null);
        state = signal(tour());
        service = jasmine.createSpyObj<GuidedTourService>(
            'GuidedTourService',
            ['useCatalogRevision', 'begin', 'visit', 'pause', 'postpone', 'skip', 'complete', 'acceptInvitation', 'declineInvitation', 'disableAutoStart', 'closePage', 'pageGuideUnavailable'],
            { session, tour: state }
        );
        ['pause', 'postpone', 'skip', 'complete', 'declineInvitation', 'disableAutoStart', 'closePage', 'pageGuideUnavailable'].forEach((method) => (service as any)[method].and.callFake(() => session.set(null)));
        router = {
            url: '/',
            navigateByUrl: jasmine.createSpy('navigateByUrl').and.callFake((url: string) => {
                router.url = url;
                return Promise.resolve(true);
            })
        };
        TestBed.configureTestingModule({
            imports: [GuidedTourComponent],
            providers: [
                provideNoopAnimations(),
                { provide: GuidedTourService, useValue: service },
                { provide: Router, useValue: router },
                { provide: KeycloakService, useValue: { currentUserFirstName: 'Maria' } },
                { provide: LayoutService, useValue: { isMobile: () => mobile } },
                {
                    provide: TenantFeatureService,
                    useValue: { isEnabled: (feature: TenantFeature) => feature !== TenantFeature.INVENTORY, current: signal({ tenantName: 'Banda Aurora' }) }
                }
            ]
        });
        fixture = TestBed.createComponent(GuidedTourComponent);
        fixture.autoDetectChanges();
    });

    afterEach(() => {
        anchors.forEach((anchor) => anchor.remove());
        fixture.destroy();
    });

    function anchor(id: string, style = 'position: fixed; top: 100px; left: 20px; width: 200px; height: 40px'): HTMLElement {
        const element = document.createElement('div');
        element.setAttribute('data-tour-anchor', id);
        element.setAttribute('style', style);
        document.body.appendChild(element);
        anchors.push(element);
        return element;
    }

    function open(automatic = true): void {
        session.set({ id: 1, presentation: 'tour', automatic });
        settle();
    }

    /** Lets navigation, anchor polling, rendering and the frame of the layout run. */
    function settle(ms = 0): void {
        tick(ms);
        fixture.detectChanges();
        tick(50);
        fixture.detectChanges();
    }

    function card(): HTMLElement {
        return fixture.nativeElement.querySelector('.guided-tour__card');
    }

    function click(label: string): void {
        const button = Array.from(card().querySelectorAll('button')).find((candidate) => candidate.textContent?.trim() === label) as HTMLButtonElement;
        expect(button).withContext(label).toBeTruthy();
        button.click();
    }

    it('welcomes the person with organization, role and an accessible dialog', fakeAsync(() => {
        open();

        expect(service.useCatalogRevision).toHaveBeenCalledWith(GUIDED_TOUR_CATALOG_REVISION);
        expect(card().getAttribute('role')).toBe('dialog');
        expect(card().getAttribute('aria-modal')).toBe('true');
        expect(card().querySelector('#guided-tour-title')?.textContent?.trim()).toBe('Benvenuto in Taurus, Maria');
        expect(card().querySelector('#guided-tour-body')?.textContent?.trim()).toBe('Sei in Banda Aurora come Utente. In circa due minuti ti mostriamo cosa puoi fare.');
        expect(card().classList).toContain('guided-tour__card--centered');
        expect(fixture.nativeElement.querySelector('.guided-tour__veil')).not.toBeNull();
        expect(document.activeElement?.textContent?.trim()).toBe('Inizia la guida');
        expect(service.visit).not.toHaveBeenCalled();
    }));

    it('starts from the welcome and highlights the menu, announcing the step', fakeAsync(() => {
        anchor('layout.menu');
        open();

        click('Inizia la guida');
        settle();

        expect(service.begin).toHaveBeenCalledWith('common.welcome');
        expect(service.visit).toHaveBeenCalledWith('common.navigation');
        expect(card().querySelector('#guided-tour-title')?.textContent?.trim()).toBe('Il menu');
        expect(fixture.nativeElement.querySelector('.guided-tour__spotlight')).not.toBeNull();
        // Next to the anchor, unless the window is too low as with a 200% zoom.
        expect(card().classList.contains('guided-tour__card--centered')).toBe(window.innerHeight < 480);
        // Without inventory the user walks through seven steps.
        expect(fixture.nativeElement.querySelector('[aria-live="polite"]').textContent.trim()).toBe('Passo 2 di 7: Il menu');
    }));

    it('falls back to the centered card when no anchor shows up within two seconds', fakeAsync(() => {
        open();
        click('Inizia la guida');
        settle(1900);
        expect(card()).toBeNull();

        settle(200);
        expect(card().classList).toContain('guided-tour__card--centered');
        expect(fixture.nativeElement.querySelector('.guided-tour__spotlight')).toBeNull();
        expect(service.visit).toHaveBeenCalledWith('common.navigation');
    }));

    it('ignores hidden or off-screen anchors', fakeAsync(() => {
        anchor('layout.menu', 'position: fixed; top: 0; left: -400px; width: 200px; height: 400px');
        anchor('layout.menuButton');
        open();
        click('Inizia la guida');
        settle();

        const spotlight: HTMLElement = fixture.nativeElement.querySelector('.guided-tour__spotlight');
        expect(spotlight.style.left).toBe('14px');
        expect(spotlight.style.width).toBe('212px');
    }));

    it('resumes from the last step and opens its page', fakeAsync(() => {
        state.set(tour({ progress: { status: 'IN_PROGRESS', seenSteps: ['common.welcome', 'common.navigation'], lastStepId: 'catalog.tracks', autoProposals: 1, catalogRevision: 1, startedAt: null, completedAt: null, version: 3 } }));
        anchor('tracks.header');
        anchor('calendar.header');
        open();

        expect(router.navigateByUrl).toHaveBeenCalledWith('/tracks');
        expect(card().querySelector('#guided-tour-title')?.textContent?.trim()).toBe('Tracce e album');
        expect(card().textContent).toContain('4 di 7');
        click('Indietro');
        settle();
        expect(router.navigateByUrl).toHaveBeenCalledWith('/calendar');
        expect(card().querySelector('#guided-tour-title')?.textContent?.trim()).toBe('Eventi e disponibilità');
    }));

    it('pauses on the step when a page with unsaved changes refuses to leave', fakeAsync(() => {
        state.set(tour({ progress: { status: 'IN_PROGRESS', seenSteps: [], lastStepId: 'calendar.availability', autoProposals: 0, catalogRevision: 1, startedAt: null, completedAt: null, version: 0 } }));
        router.url = '/profile';
        router.navigateByUrl.and.resolveTo(false);
        open();

        expect(service.pause).toHaveBeenCalled();
        expect(card()).toBeNull();
    }));

    it('points at the page header below 992 px', fakeAsync(() => {
        mobile = true;
        state.set(tour({ role: RoleEnums.ADMIN, progress: { status: 'IN_PROGRESS', seenSteps: [], lastStepId: 'admin.users', autoProposals: 0, catalogRevision: 1, startedAt: null, completedAt: null, version: 0 } }));
        anchor('users.create', 'position: fixed; top: 300px; left: 20px; width: 100px; height: 40px');
        anchor('users.header', 'position: fixed; top: 60px; left: 20px; width: 300px; height: 50px');
        open();

        const spotlight: HTMLElement = fixture.nativeElement.querySelector('.guided-tour__spotlight');
        expect(spotlight.style.top).toBe('54px');
    }));

    it('closes on Esc: a pause on a step, a postponement on the welcome', fakeAsync(() => {
        open();
        document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
        expect(service.postpone).toHaveBeenCalled();
        expect(service.pause).not.toHaveBeenCalled();

        state.set(tour({ progress: { status: 'IN_PROGRESS', seenSteps: [], lastStepId: 'common.help', autoProposals: 0, catalogRevision: 1, startedAt: null, completedAt: null, version: 0 } }));
        anchor('topbar.help');
        session.set({ id: 2, presentation: 'tour', automatic: false });
        settle();
        document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
        expect(service.pause).toHaveBeenCalled();
    }));

    it('concludes on the last step and gives the focus back', fakeAsync(() => {
        const opener = document.createElement('button');
        document.body.appendChild(opener);
        opener.focus();
        state.set(tour({ progress: { status: 'IN_PROGRESS', seenSteps: [], lastStepId: 'common.help', autoProposals: 0, catalogRevision: 1, startedAt: null, completedAt: null, version: 0 } }));
        anchor('topbar.help');
        open(false);

        click('Fine');
        settle();
        expect(service.complete).toHaveBeenCalledWith('common.help');
        expect(card()).toBeNull();
        expect(document.activeElement).toBe(opener);
        opener.remove();
    }));

    it('offers to switch the automatic start off only while it is on', fakeAsync(() => {
        open();
        click('Non avviare più la guida automaticamente');
        expect(service.disableAutoStart).toHaveBeenCalled();

        state.set(tour({ settings: { autoStartEnabled: false, legacyInvitation: false, version: 1 } }));
        open(false);
        expect(Array.from(card().querySelectorAll('button')).map((button) => button.textContent?.trim())).not.toContain('Non avviare più la guida automaticamente');
    }));

    describe('page guide', () => {
        const step = (id: string, anchor: string): GuidedTourStep => ({ id, anchors: [anchor], mobileAnchors: [anchor], icon: 'pi pi-info-circle', title: id, body: `Testo di ${id}`, points: [] });
        const steps = [step('page.event.data', 'event.data'), step('page.event.preparation', 'event.preparation'), step('page.event.presence', 'event.presence')];

        function openPage(): void {
            session.set({ id: 3, presentation: 'page', automatic: false, steps, path: '/calendar/7' });
            settle();
        }

        function title(): string | undefined {
            return card().querySelector('#guided-tour-title')?.textContent?.trim();
        }

        it('speaks only of what the page shows, from the first step, and saves nothing', fakeAsync(() => {
            anchor('event.data');
            anchor('event.presence', 'position: fixed; top: 300px; left: 20px; width: 200px; height: 40px');
            openPage();

            expect(title()).toBe('page.event.data');
            expect(card().textContent).toContain('1 di 2');
            expect(Array.from(card().querySelectorAll('button')).map((button) => button.textContent?.trim())).not.toContain('Indietro');
            click('Avanti');
            settle();
            expect(title()).toBe('page.event.presence');
            click('Indietro');
            settle();
            expect(title()).toBe('page.event.data');
            expect(router.navigateByUrl).not.toHaveBeenCalled();
            expect(service.visit).not.toHaveBeenCalled();
            expect(service.begin).not.toHaveBeenCalled();
        }));

        it('closes with «Chiudi», with Esc and with «Fine», never skipping the guided tour', fakeAsync(() => {
            anchor('event.data');
            openPage();
            click('Chiudi');
            expect(service.closePage).toHaveBeenCalledTimes(1);

            openPage();
            document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
            expect(service.closePage).toHaveBeenCalledTimes(2);

            openPage();
            click('Fine');
            expect(service.closePage).toHaveBeenCalledTimes(3);
            expect(service.skip).not.toHaveBeenCalled();
            expect(service.pause).not.toHaveBeenCalled();
            expect(service.complete).not.toHaveBeenCalled();
        }));

        it('waits two seconds for a page still loading, then says the guide is not available', fakeAsync(() => {
            openPage();
            settle(1500);
            expect(card()).toBeNull();
            anchor('event.preparation');
            settle(100);
            expect(title()).toBe('page.event.preparation');
            expect(card().textContent).toContain('1 di 1');
            click('Fine');

            anchors.forEach((element) => element.remove());
            openPage();
            settle(2100);
            expect(service.pageGuideUnavailable).toHaveBeenCalled();
            expect(card()).toBeNull();
        }));

        it('leaves out the steps whose anchor is hidden, as in a closed tab', fakeAsync(() => {
            anchor('event.data');
            anchor('event.preparation', 'display: none');
            openPage();

            expect(card().textContent).toContain('1 di 1');
        }));
    });

    it('invites the members present at the release with a banner', fakeAsync(() => {
        state.set(tour({ role: RoleEnums.ARCHIVIST, settings: { autoStartEnabled: true, legacyInvitation: true, version: 0 } }));
        session.set({ id: 1, presentation: 'invitation', automatic: false });
        settle();

        const banner: HTMLElement = fixture.nativeElement.querySelector('app-inline-alert');
        expect(banner.textContent).toContain('Una guida ti mostra in due minuti cosa puoi fare come Archivista.');
        expect(card()).toBeNull();
        const buttons = Array.from(banner.querySelectorAll('button'));
        buttons.find((button) => button.textContent?.trim() === 'No, grazie')!.click();
        expect(service.declineInvitation).toHaveBeenCalled();
    }));
});

describe('place', () => {
    const viewport = { width: 1280, height: 800 };

    it('prefers the right of the highlighted box', () => {
        expect(place({ top: 100, left: 20, width: 200, height: 40 }, 352, 200, viewport, 12, 16, false)).toEqual({ top: 20, left: 232 });
    });

    it('goes below, then above, then left when the right has no room', () => {
        const wide = { top: 100, left: 20, width: 1200, height: 40 };
        expect(place(wide, 352, 200, viewport, 12, 16, false)).toEqual({ top: 152, left: 444 });
        expect(place({ ...wide, top: 600 }, 352, 200, viewport, 12, 16, false)).toEqual({ top: 388, left: 444 });
        expect(place({ top: 100, left: 900, width: 360, height: 650 }, 352, 200, viewport, 12, 16, false)).toEqual({ top: 325, left: 536 });
    });

    it('centers the card when no side has room', () => {
        expect(place({ top: 0, left: 0, width: 1280, height: 800 }, 352, 200, viewport, 12, 16, false)).toBeNull();
    });

    it('keeps the full width below or above on small screens', () => {
        expect(place({ top: 60, left: 20, width: 300, height: 50 }, 343, 200, { width: 375, height: 700 }, 12, 16, true)).toEqual({ top: 122, left: 16 });
    });
});
