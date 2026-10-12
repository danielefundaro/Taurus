import { TestBed } from '@angular/core/testing';
import { NEVER, of } from 'rxjs';
import { Preferences, TenantStatus } from '../module';
import { LayoutService } from './layout.service';
import { LocalStorageService } from './local-storage.service';
import { PreferencesService } from './preferences.service';
import { TenantStatusService } from './tenant-status.service';

describe('LayoutService', () => {
    let preferencesService: jasmine.SpyObj<PreferencesService>;
    let storage: Map<string, Preferences>;
    let tenantStatus: TenantStatus;
    let service: LayoutService;

    beforeEach(() => {
        // Headless Chrome does not render view transitions: run the update callback directly
        spyOn(document as any, 'startViewTransition').and.callFake((update: () => void) => {
            update();
            return { finished: Promise.resolve() };
        });
        storage = new Map();
        tenantStatus = { tenantCode: 'tenant-a', provisioned: true };
        preferencesService = jasmine.createSpyObj<PreferencesService>('PreferencesService', ['count', 'getAll', 'create', 'update']);
        preferencesService.count.and.returnValue(NEVER);
        const localStorageService = jasmine.createSpyObj<LocalStorageService>('LocalStorageService', ['getItem', 'setItem']);
        localStorageService.getItem.and.callFake((key: string) => storage.get(key) ?? null);
        localStorageService.setItem.and.callFake((key: string, value: Preferences) => {
            storage.set(key, value);
        });

        TestBed.configureTestingModule({
            providers: [
                { provide: PreferencesService, useValue: preferencesService },
                { provide: LocalStorageService, useValue: localStorageService },
                { provide: TenantStatusService, useValue: { load: () => of(tenantStatus) } }
            ]
        });
        service = TestBed.inject(LayoutService);
        TestBed.flushEffects();
    });

    it('keeps a preference without id local instead of updating it on the server', () => {
        storage.set('primary', { key: 'primary', value: 'emerald' } as Preferences);

        service.layoutConfig.update((config) => ({ ...config, primary: 'blue' }));
        TestBed.flushEffects();

        expect(preferencesService.update).not.toHaveBeenCalled();
        expect(storage.get('primary')?.value).toBe('blue');
    });

    it('updates a persisted preference on the server', () => {
        storage.set('primary', { id: 3, key: 'primary', value: 'emerald' } as Preferences);
        preferencesService.update.and.returnValue(of({ id: 3, key: 'primary', value: 'blue' } as Preferences));

        service.layoutConfig.update((config) => ({ ...config, primary: 'blue' }));
        TestBed.flushEffects();

        expect(preferencesService.update).toHaveBeenCalledOnceWith(3, jasmine.objectContaining({ key: 'primary', value: 'blue' }));
        expect(storage.get('primary')).toEqual(jasmine.objectContaining({ id: 3, value: 'blue' }));
    });

    it('does not touch the server while the tenant has no schema', () => {
        tenantStatus = { tenantCode: 'temp', provisioned: false };
        storage.set('primary', { id: 3, key: 'primary', value: 'emerald' } as Preferences);
        storage.delete('preset');
        preferencesService.count.calls.reset();

        service.layoutConfig.update((config) => ({ ...config, preset: 'Lara', primary: 'blue' }));
        TestBed.flushEffects();

        expect(preferencesService.update).not.toHaveBeenCalled();
        expect(preferencesService.count).not.toHaveBeenCalled();
        expect(storage.get('primary')?.value).toBe('blue');
        expect(storage.get('preset')?.value).toBe('Lara');
    });
});
