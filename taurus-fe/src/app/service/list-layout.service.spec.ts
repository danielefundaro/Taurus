import { of } from 'rxjs';
import { Preferences } from '../module';
import { ListLayoutService } from './list-layout.service';
import { LocalStorageService } from './local-storage.service';
import { PreferencesService } from './preferences.service';

describe('ListLayoutService', () => {
    let preferencesService: jasmine.SpyObj<PreferencesService>;
    let localStorageService: jasmine.SpyObj<LocalStorageService>;
    let service: ListLayoutService;

    beforeEach(() => {
        preferencesService = jasmine.createSpyObj<PreferencesService>('PreferencesService', ['count', 'getAll', 'create', 'update']);
        localStorageService = jasmine.createSpyObj<LocalStorageService>('LocalStorageService', ['getItem', 'setItem']);
        localStorageService.getItem.and.returnValue(null);
        service = new ListLayoutService(preferencesService, localStorageService);
    });

    it('hydrates only valid list layout preferences', () => {
        service.hydrate([{ key: 'listLayout.albums', value: 'grid' } as Preferences, { key: 'listLayout.tracks', value: 'invalid' } as Preferences, { key: 'primary', value: 'emerald' } as Preferences]);

        expect(service.hydrated()).toBeTrue();
        expect(service.get('albums')).toBe('grid');
        expect(service.get('tracks')).toBe('list');
    });

    it('uses the local cache before hydration', () => {
        localStorageService.getItem.and.returnValue({ key: 'listLayout.inventory', value: 'grid' } as Preferences);

        expect(service.get('inventory')).toBe('grid');
    });

    it('applies the layout again once hydration completes', () => {
        const applied: string[] = [];

        service.observe('albums', (value) => applied.push(value));
        expect(applied).toEqual(['list']);

        service.hydrate([{ key: 'listLayout.albums', value: 'grid' } as Preferences]);
        expect(applied).toEqual(['list', 'grid']);
    });

    it('does not re-apply a layout the user changed before hydration', () => {
        const applied: string[] = [];
        localStorageService.getItem.and.returnValue(null);
        preferencesService.count.and.returnValue(of(0));
        preferencesService.getAll.and.returnValue(of({ content: [], totalElements: 0 } as any));
        preferencesService.create.and.returnValue(of({ id: 1, key: 'listLayout.albums', value: 'grid' } as Preferences));

        service.observe('albums', (value) => applied.push(value));
        service.set('albums', 'grid');
        service.hydrate([{ key: 'listLayout.albums', value: 'list' } as Preferences]);

        expect(applied).toEqual(['list']);
        expect(service.get('albums')).toBe('grid');
    });

    it('does not register a callback when preferences are already hydrated', () => {
        const applied: string[] = [];
        service.hydrate([{ key: 'listLayout.tracks', value: 'grid' } as Preferences]);

        service.observe('tracks', (value) => applied.push(value));
        service.hydrate([{ key: 'listLayout.tracks', value: 'list' } as Preferences]);

        expect(applied).toEqual(['grid']);
    });

    it('updates an existing persisted preference', () => {
        const preference = { id: 7, key: 'listLayout.users', value: 'list' } as Preferences;
        localStorageService.getItem.and.returnValue(preference);
        preferencesService.update.and.returnValue(of({ ...preference, value: 'grid' }));

        service.set('users', 'grid');

        expect(service.get('users')).toBe('grid');
        expect(preferencesService.update).toHaveBeenCalledWith(7, jasmine.objectContaining({ key: 'listLayout.users', value: 'grid' }));
        expect(localStorageService.setItem).toHaveBeenCalled();
    });
});
