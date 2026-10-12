import { HttpClient } from '@angular/common/http';
import { computed, Injectable, signal } from '@angular/core';
import { catchError, finalize, Observable, of, shareReplay, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { TenantStatus } from '../module';

/**
 * Stato del tenant della sessione. Finché il tenant non ha uno schema attivo, come al primo accesso con il tenant
 * di avvio, l'app lavora in modalità bootstrap: offre solo la gestione dei tenant e non chiama le API del tenant.
 */
@Injectable({ providedIn: 'root' })
export class TenantStatusService {
    private readonly status = signal<TenantStatus | null>(null);
    private inFlight?: Observable<TenantStatus>;

    /** Vero solo quando il backend ha confermato che il tenant non ha uno schema attivo. */
    readonly bootstrap = computed(() => this.status()?.provisioned === false);
    readonly tenantCode = computed(() => this.status()?.tenantCode ?? null);

    constructor(private readonly http: HttpClient) {}

    load(): Observable<TenantStatus> {
        const cached = this.status();
        if (cached) return of(cached);
        if (this.inFlight) return this.inFlight;

        this.inFlight = this.http.get<TenantStatus>(`${environment.baseUrl}/tenant-status`).pipe(
            tap((status) => this.status.set(status)),
            // Se lo stato non è leggibile si resta nel funzionamento normale: il backend protegge comunque le API.
            catchError(() => of<TenantStatus>({ tenantCode: null, provisioned: true })),
            finalize(() => (this.inFlight = undefined)),
            shareReplay({ bufferSize: 1, refCount: false })
        );
        return this.inFlight;
    }

    /** Dimentica lo stato: la prossima `load()` lo rilegge dal backend. */
    invalidate(): void {
        this.status.set(null);
    }
}
