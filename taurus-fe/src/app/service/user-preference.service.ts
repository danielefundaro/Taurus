import { Injectable } from '@angular/core';
import { Observable, first, map, of, switchMap, tap } from 'rxjs';
import { Preferences, PreferencesCriteria } from '../module';
import { LocalStorageService } from './local-storage.service';
import { PreferencesService } from './preferences.service';

/**
 * Lettura e scrittura di una singola preferenza utente per chiave.
 *
 * La copia locale idratata all'avvio dell'applicazione è la prima fonte; quando
 * manca si interroga il backend. La scrittura crea la riga la prima volta e la
 * aggiorna in seguito, così le pagine non devono conoscere la tabella preferenze.
 * L'elenco viene recuperato per intero perché `PreferencesService` non serializza
 * i filtri per campo.
 */
@Injectable({ providedIn: 'root' })
export class UserPreferenceService {
    constructor(
        private readonly preferencesService: PreferencesService,
        private readonly localStorageService: LocalStorageService
    ) {}

    get(key: string): Observable<string | undefined> {
        return this.find(key).pipe(map((preference) => preference?.value));
    }

    set(key: string, value: string): Observable<Preferences> {
        return this.find(key).pipe(
            switchMap((existing) => {
                if (existing?.id) {
                    const updated: Preferences = { ...existing, value } as Preferences;
                    return this.preferencesService.update(existing.id, updated);
                }
                const preference = new Preferences();
                preference.key = key;
                preference.value = value;
                return this.preferencesService.create(preference);
            }),
            first(),
            tap((saved) => this.localStorageService.setItem(key, saved))
        );
    }

    private find(key: string): Observable<Preferences | undefined> {
        const cached = this.localStorageService.getItem(key);
        if (cached?.id) return of(cached);

        return this.preferencesService.count().pipe(
            first(),
            switchMap((count) => {
                const criteria = new PreferencesCriteria();
                criteria.page = 0;
                criteria.size = Math.max(count, 1);
                return this.preferencesService.getAll(criteria).pipe(first());
            }),
            tap((page) => page.content.forEach((preference) => this.localStorageService.setItem(preference.key, preference))),
            map((page) => page.content.find((preference) => preference.key === key))
        );
    }
}
