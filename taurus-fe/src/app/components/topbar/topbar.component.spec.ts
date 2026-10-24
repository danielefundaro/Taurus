import { signal } from '@angular/core';
import { ComponentFixture, DeferBlockBehavior, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { RoleEnums } from '../../constants';
import { KeycloakService, LayoutService, TenantFeatureService, TenantStatusService } from '../../service';
import { TopbarComponent } from './topbar.component';

describe('TopbarComponent', () => {
    let bootstrap: ReturnType<typeof signal<boolean>>;
    let keycloakService: jasmine.SpyObj<KeycloakService>;
    let fixture: ComponentFixture<TopbarComponent>;

    beforeEach(() => {
        bootstrap = signal(false);
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
                { provide: LayoutService, useValue: { isDarkTheme: signal(false), notificationNumber: signal(0) } },
                { provide: TenantFeatureService, useValue: { refresh: () => of({}), current: signal({ tenantName: 'Orchestra Aurora' }) } },
                { provide: TenantStatusService, useValue: { bootstrap } }
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

        expect(actionLabels()).toEqual(['Calendar', 'Notifiche', 'Profilo']);
        expect(fixture.nativeElement.querySelector('.topbar-tenant-name')?.textContent.trim()).toBe('Orchestra Aurora');
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
});
