import { HttpClient } from '@angular/common/http';
import { computed, Injectable, signal } from '@angular/core';
import { catchError, EMPTY, filter, finalize, Observable, of, shareReplay, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { publicPage } from '../constants/public-pages';
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

    /**
     * Emette lo stato solo dove si leggono e salvano i dati del tenant: non in modalità bootstrap, dove il tenant non
     * ha schema, e non in una pagina pubblica, che non ha sessione. Lì `load()` non riesce a leggere lo stato e ricade
     * sul funzionamento normale, e chi salva le preferenze del layout riceverebbe solo i 401 dell'interceptor.
     */
    whenProvisioned(): Observable<TenantStatus> {
        if (publicPage()) return EMPTY;
        return this.load().pipe(filter((status) => status.provisioned));
    }

    /** Dimentica lo stato: la prossima `load()` lo rilegge dal backend. */
    invalidate(): void {
        this.status.set(null);
    }
}
