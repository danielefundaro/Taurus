import { HttpClient } from '@angular/common/http';
import { computed, Injectable, signal } from '@angular/core';
import { finalize, interval, Observable, of, shareReplay, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { TenantFeatures } from '../module';

@Injectable({ providedIn: 'root' })
export class TenantFeatureService {
    private static readonly maxAgeMs = 60_000;
    private readonly features = signal<TenantFeatures | null>(null);
    private lastLoadedAt = 0;
    private inFlight?: Observable<TenantFeatures>;

    readonly loaded = computed(() => this.features() !== null);
    readonly financeEnabled = computed(() => this.features()?.financeEnabled === true);
    readonly inventoryEnabled = computed(() => this.features()?.inventoryEnabled === true);
    readonly current = this.features.asReadonly();

    constructor(private readonly http: HttpClient) {
        interval(TenantFeatureService.maxAgeMs).subscribe(() => {
            if (document.visibilityState === 'visible') this.refresh().subscribe({ error: () => undefined });
        });
        document.addEventListener('visibilitychange', () => {
            if (document.visibilityState === 'visible') this.refresh().subscribe({ error: () => undefined });
        });
    }

    refresh(force = false): Observable<TenantFeatures> {
        const cached = this.features();
        if (!force && cached && Date.now() - this.lastLoadedAt < TenantFeatureService.maxAgeMs) return of(cached);
        if (this.inFlight) return this.inFlight;

        this.inFlight = this.http.get<TenantFeatures>(`${environment.baseUrl}/tenant-features/current`).pipe(
            tap((features) => {
                this.features.set(features);
                this.lastLoadedAt = Date.now();
            }),
            finalize(() => (this.inFlight = undefined)),
            shareReplay({ bufferSize: 1, refCount: false })
        );
        return this.inFlight;
    }
}
