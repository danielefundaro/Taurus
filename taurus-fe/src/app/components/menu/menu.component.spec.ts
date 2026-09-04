import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { TenantFeatureService } from '../../service';
import { MenuComponent } from './menu.component';

describe('MenuComponent', () => {
    it('updates feature entries when flags change', () => {
        const loaded = signal(false);
        const financeEnabled = signal(false);
        const inventoryEnabled = signal(false);
        const service = {
            loaded,
            financeEnabled,
            inventoryEnabled,
            refresh: jasmine.createSpy().and.returnValue(of({}))
        };
        TestBed.configureTestingModule({
            providers: [{ provide: TenantFeatureService, useValue: service }]
        });
        const component = TestBed.runInInjectionContext(() => new MenuComponent(service as never));
        TestBed.flushEffects();

        expect(menuItem(component, 'Economia')?.visible).toBeFalse();
        expect(menuItem(component, 'Inventario')?.visible).toBeFalse();

        loaded.set(true);
        financeEnabled.set(true);
        TestBed.flushEffects();

        expect(menuItem(component, 'Economia')?.visible).toBeTrue();
        expect(menuItem(component, 'Inventario')?.visible).toBeFalse();

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

function menuItem(component: MenuComponent, label: string) {
    return component.model.flatMap((section) => section.items ?? []).find((item) => item.label === label);
}
