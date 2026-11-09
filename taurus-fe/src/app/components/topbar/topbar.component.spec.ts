import { signal } from '@angular/core';
import { ComponentFixture, DeferBlockBehavior, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { RoleEnums } from '../../constants';
import { GuidedTourService, KeycloakService, LayoutService, TenantFeatureService, TenantStatusService } from '../../service';
import { TopbarComponent } from './topbar.component';

describe('TopbarComponent', () => {
    let bootstrap: ReturnType<typeof signal<boolean>>;
    let attention: ReturnType<typeof signal<boolean>>;
    let calendarEnabled: ReturnType<typeof signal<boolean>>;
    let notificationNumber: ReturnType<typeof signal<number>>;
    let guidedTour: jasmine.SpyObj<GuidedTourService>;
    let keycloakService: jasmine.SpyObj<KeycloakService>;
    let fixture: ComponentFixture<TopbarComponent>;

    beforeEach(() => {
        bootstrap = signal(false);
        attention = signal(false);
        calendarEnabled = signal(true);
        notificationNumber = signal(0);
        guidedTour = jasmine.createSpyObj<GuidedTourService>('GuidedTourService', ['open', 'openPage', 'pageGuideAvailable'], { attention });
        guidedTour.pageGuideAvailable.and.resolveTo(false);
        guidedTour.openPage.and.resolveTo();
        keycloakService = jasmine.createSpyObj<KeycloakService>('KeycloakService', ['logout'], {
            currentUserFirstName: 'Daniele',
            currentUserLastName: 'Rossi',
            currentUserRole: RoleEnums.TREASURER,
            currentUserRoleChosen: false
        });
        keycloakService.logout.and.resolveTo();
        TestBed.configureTestingModule({
            imports: [TopbarComponent],
            deferBlockBehavior: DeferBlockBehavior.Manual,
            providers: [
                provideRouter([]),
                { provide: KeycloakService, useValue: keycloakService },
                { provide: LayoutService, useValue: { isDarkTheme: signal(false), notificationNumber } },
                { provide: TenantFeatureService, useValue: { refresh: () => of({}), current: signal({ tenantName: 'Orchestra Aurora' }), loaded: signal(true), calendarEnabled } },
                { provide: TenantStatusService, useValue: { bootstrap } },
                { provide: GuidedTourService, useValue: guidedTour }
            ]
        });
        fixture = TestBed.createComponent(TopbarComponent);
    });

    function actionLabels(): string[] {
        const actions = fixture.nativeElement.querySelectorAll('.layout-topbar-menu-content .layout-topbar-action span') as NodeListOf<HTMLElement>;
        return Array.from(actions).map((action) => action.textContent?.trim() ?? '');
    }

    it('offers calendar, notifications and profile for a provisioned tenant', () => {
        fixture.detectChanges();

        expect(actionLabels()).toEqual(['Calendario', 'Notifiche', 'Profilo']);
        expect(fixture.nativeElement.querySelector('.topbar-tenant-name')?.textContent.trim()).toBe('Orchestra Aurora');
    });

    it('leaves the calendar shortcut out when the tenant has no calendar', () => {
        calendarEnabled.set(false);
        fixture.detectChanges();

        expect(actionLabels()).toEqual(['Notifiche', 'Profilo']);
    });

    it('names the notifications action with the unread count that its badge shows', () => {
        fixture.detectChanges();
        const action = (): HTMLElement => fixture.nativeElement.querySelector('.layout-topbar-action[pTooltip="Notifiche"]');

        expect(action().getAttribute('aria-label')).toBe('Notifiche');
        expect(action().textContent?.trim()).toBe('Notifiche');

        notificationNumber.set(3);
        fixture.detectChanges();
        expect(action().getAttribute('aria-label')).toBe('Notifiche, 3 da leggere');
        // Label and badge stay two words: axe compares the visible text with the name word by word.
        expect(action().textContent?.replace(/\s+/g, ' ').trim()).toBe('Notifiche 3');
    });

    it('adds the role to the tenant name only when the user chose it among more roles of the tenant', () => {
        (Object.getOwnPropertyDescriptor(keycloakService, 'currentUserRoleChosen')?.get as jasmine.Spy).and.returnValue(true);
        fixture.detectChanges();

        const tenant: HTMLElement = fixture.nativeElement.querySelector('.topbar-tenant-name');
        expect(tenant.querySelector('.topbar-tenant-name__role')?.textContent?.trim()).toBe('· Tesoriere');
        expect(tenant.title).toBe('Orchestra Aurora · Tesoriere');

        bootstrap.set(true);
        fixture.detectChanges();
        expect(tenant.querySelector('.topbar-tenant-name__role')).toBeNull();
    });

    it('keeps a logout action in bootstrap mode, where the profile is not reachable', () => {
        bootstrap.set(true);
        fixture.detectChanges();

        expect(actionLabels()).toEqual(['Esci']);
        expect(fixture.nativeElement.querySelector('.topbar-tenant-name')?.textContent.trim()).toBe('Primo accesso');

        (fixture.nativeElement.querySelector('button[aria-label="Esci"]') as HTMLButtonElement).click();

        expect(keycloakService.logout).toHaveBeenCalled();
    });

    describe('help button', () => {
        const help = (): HTMLButtonElement => fixture.nativeElement.querySelector('[data-tour-anchor="topbar.help"]');
        const menuItems = (): HTMLButtonElement[] => Array.from(fixture.nativeElement.querySelectorAll('.topbar-help-menu [role="menuitem"]'));

        it('opens the guided tour from its menu, which then closes', () => {
            fixture.detectChanges();
            expect(help().getAttribute('aria-expanded')).toBe('false');

            help().click();
            fixture.detectChanges();
            expect(help().getAttribute('aria-expanded')).toBe('true');
            expect(menuItems().map((item) => item.textContent?.trim())).toEqual(['Guida introduttiva']);

            menuItems()[0].click();
            fixture.detectChanges();
            expect(guidedTour.open).toHaveBeenCalled();
            expect(menuItems()).toEqual([]);
        });

        it('adds the guide of the page when the page has one', async () => {
            guidedTour.pageGuideAvailable.and.resolveTo(true);
            fixture.detectChanges();
            help().click();
            await fixture.whenStable();
            fixture.detectChanges();
            expect(menuItems().map((item) => item.textContent?.trim())).toEqual(['Guida introduttiva', 'Guida di questa pagina']);

            menuItems()[1].click();
            fixture.detectChanges();
            expect(guidedTour.openPage).toHaveBeenCalled();
            expect(guidedTour.open).not.toHaveBeenCalled();
            expect(menuItems()).toEqual([]);
        });

        it('closes the menu on a click outside and on Esc', () => {
            fixture.detectChanges();
            help().click();
            fixture.detectChanges();
            document.body.click();
            fixture.detectChanges();
            expect(menuItems()).toEqual([]);

            help().click();
            fixture.detectChanges();
            document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
            fixture.detectChanges();
            expect(menuItems()).toEqual([]);
            expect(guidedTour.open).not.toHaveBeenCalled();
        });

        it('shows a dot while the tour waits to be concluded', () => {
            fixture.detectChanges();
            expect(fixture.nativeElement.querySelector('.topbar-help-dot')).toBeNull();
            expect(help().getAttribute('aria-label')).toBe('Aiuto');

            attention.set(true);
            fixture.detectChanges();
            expect(fixture.nativeElement.querySelector('.topbar-help-dot')).not.toBeNull();
            expect(help().getAttribute('aria-label')).toBe('Aiuto, guida da completare');
        });

        it('is not offered in bootstrap mode', () => {
            bootstrap.set(true);
            fixture.detectChanges();
            expect(help()).toBeNull();
        });
    });
});
