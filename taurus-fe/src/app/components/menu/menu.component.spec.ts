import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { TenantFeatureService, TenantStatusService } from '../../service';
import { MenuComponent } from './menu.component';

describe('MenuComponent', () => {
    it('updates feature entries when flags change', () => {
        const loaded = signal(false);
        const financeEnabled = signal(false);
        const inventoryEnabled = signal(false);
        const onboardingImportEnabled = signal(false);
        const externalCalendarFeedEnabled = signal(false);
        const service = {
            loaded,
            financeEnabled,
            inventoryEnabled,
            onboardingImportEnabled,
            externalCalendarFeedEnabled,
            refresh: jasmine.createSpy().and.returnValue(of({}))
        };
        TestBed.configureTestingModule({
            providers: [
                { provide: TenantFeatureService, useValue: service },
                { provide: TenantStatusService, useValue: { bootstrap: signal(false) } }
            ]
        });
        const component = TestBed.runInInjectionContext(() => new MenuComponent(service as never));
        TestBed.flushEffects();

        expect(menuItem(component, 'Economia')?.visible).toBeFalse();
        expect(menuItem(component, 'Inventario')?.visible).toBeFalse();
        expect(menuItem(component, 'Configurazione iniziale')?.visible).toBeFalse();

        loaded.set(true);
        financeEnabled.set(true);
        onboardingImportEnabled.set(true);
        TestBed.flushEffects();

        expect(menuItem(component, 'Economia')?.visible).toBeTrue();
        expect(menuItem(component, 'Inventario')?.visible).toBeFalse();
        expect(menuItem(component, 'Configurazione iniziale')?.visible).toBeTrue();

        inventoryEnabled.set(true);
        TestBed.flushEffects();

        expect(menuItem(component, 'Economia')?.visible).toBeTrue();
        expect(menuItem(component, 'Inventario')?.visible).toBeTrue();

        financeEnabled.set(false);
        TestBed.flushEffects();

        expect(menuItem(component, 'Economia')?.visible).toBeFalse();
        expect(menuItem(component, 'Inventario')?.visible).toBeTrue();
    });
});

describe('MenuComponent in bootstrap mode', () => {
    it('offers only tenant management while the tenant has no schema', () => {
        const bootstrap = signal(true);
        const service = {
            loaded: signal(false),
            financeEnabled: signal(false),
            inventoryEnabled: signal(false),
            onboardingImportEnabled: signal(false),
            externalCalendarFeedEnabled: signal(false),
            refresh: jasmine.createSpy().and.returnValue(of({}))
        };
        TestBed.configureTestingModule({
            providers: [
                { provide: TenantFeatureService, useValue: service },
                { provide: TenantStatusService, useValue: { bootstrap } }
            ]
        });
        const component = TestBed.runInInjectionContext(() => new MenuComponent(service as never));
        TestBed.flushEffects();

        expect(component.model.flatMap((section) => section.items ?? []).map((item) => item.label)).toEqual(['Tenant']);

        bootstrap.set(false);
        TestBed.flushEffects();

        expect(menuItem(component, 'Dashboard')).toBeDefined();
        expect(menuItem(component, 'Tenant')).toBeDefined();
    });
});

function menuItem(component: MenuComponent, label: string) {
    return component.model.flatMap((section) => section.items ?? []).find((item) => item.label === label);
}
