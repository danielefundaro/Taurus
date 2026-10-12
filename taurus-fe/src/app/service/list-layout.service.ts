import { Injectable, signal } from '@angular/core';
import { filter, first, of, switchMap } from 'rxjs';
import { Preferences, PreferencesCriteria } from '../module';
import { LocalStorageService } from './local-storage.service';
import { PreferencesService } from './preferences.service';
import { TenantStatusService } from './tenant-status.service';

export type ListLayout = 'list' | 'grid';

const KEY_PREFIX = 'listLayout.';

@Injectable({ providedIn: 'root' })
export class ListLayoutService {
    readonly hydrated = signal(false);
    private readonly values = signal<Record<string, ListLayout>>({});
    private readonly pending = new Map<string, ((value: ListLayout) => void)[]>();
    private readonly touched = new Set<string>();

    constructor(
        private readonly preferencesService: PreferencesService,
        private readonly localStorageService: LocalStorageService,
        private readonly tenantStatus: TenantStatusService
    ) { }

    hydrate(preferences: Preferences[]): void {
        const next = { ...this.values() };
        preferences
            .filter((preference) => preference.key.startsWith(KEY_PREFIX))
            .forEach((preference) => {
                const page = preference.key.substring(KEY_PREFIX.length);
                if (this.touched.has(page)) return;
                if (preference.value === 'list' || preference.value === 'grid') next[page] = preference.value;
            });
        this.values.set(next);
        this.hydrated.set(true);
        this.flushPending();
    }

    get(page: string): ListLayout {
        const cached = this.localStorageService.getItem(KEY_PREFIX + page)?.value;
        return this.values()[page] ?? (cached === 'grid' ? 'grid' : 'list');
    }

    /**
     * Applica subito la vista conosciuta e, se le preferenze non sono ancora
     * arrivate dal backend, la riapplica appena l'idratazione si completa:
     * una pagina raggiunta direttamente per URL non resta sul valore
     * predefinito solo perché la risposta è arrivata dopo il primo rendering.
     */
    observe(page: string, apply: (value: ListLayout) => void): void {
        apply(this.get(page));

        if (this.hydrated()) return;

        const callbacks = this.pending.get(page) ?? [];
        callbacks.push(apply);
        this.pending.set(page, callbacks);
    }

    set(page: string, value: ListLayout): void {
        const key = KEY_PREFIX + page;
        this.values.update((current) => ({ ...current, [page]: value }));
        this.pending.delete(page);
        this.touched.add(page);
        let preference = this.localStorageService.getItem(key);
        if (!preference) {
            preference = new Preferences();
            preference.key = key;
            preference.value = value;
            this.localStorageService.setItem(key, preference);
            this.tenantStatus
                .load()
                .pipe(
                    // In modalità bootstrap la preferenza resta solo locale: il tenant non ha schema.
                    filter((status) => status.provisioned),
                    switchMap(() => this.preferencesService.count().pipe(first())),
                    switchMap((count) => {
                        const criteria = new PreferencesCriteria();
                        criteria.page = 0;
                        criteria.size = count;
                        return this.preferencesService.getAll(criteria).pipe(first());
                    }),
                    switchMap((result) => (result.content.some((p) => p.key === key) ? of(undefined) : this.preferencesService.create(preference!)))
                )
                .subscribe((result) => {
                    if (result) this.localStorageService.setItem(key, result);
                });
            return;
        }
        preference.value = value;
        this.localStorageService.setItem(key, preference);
        const { id } = preference;
        if (id)
            this.tenantStatus
                .load()
                .pipe(
                    filter((status) => status.provisioned),
                    switchMap(() => this.preferencesService.update(id, preference!).pipe(first()))
                )
                .subscribe((result) => this.localStorageService.setItem(key, result));
    }

    private flushPending(): void {
        this.pending.forEach((callbacks, page) => {
            const value = this.get(page);
            callbacks.forEach((apply) => apply(value));
        });
        this.pending.clear();
    }
}
