import { HttpClient } from '@angular/common/http';
import { computed, Injectable, signal } from '@angular/core';
import { finalize, interval, Observable, of, shareReplay, tap } from 'rxjs';
import { environment } from '../../environments/environment';
import { TenantFeature, TenantFeatureCapabilities, TenantFeatures } from '../module';

@Injectable({ providedIn: 'root' })
export class TenantFeatureService {
    private static readonly maxAgeMs = 60_000;
    private readonly features = signal<TenantFeatures | null>(null);
    private readonly capabilityState = signal<TenantFeatureCapabilities | null>(null);
    private lastLoadedAt = 0;
    private inFlight?: Observable<TenantFeatures>;

    readonly loaded = computed(() => this.features() !== null);
    readonly financeEnabled = computed(() => this.features()?.financeEnabled === true);
    readonly inventoryEnabled = computed(() => this.features()?.inventoryEnabled === true);
    readonly onboardingImportEnabled = computed(() => this.features()?.onboardingImportEnabled === true);
    readonly externalCalendarFeedEnabled = computed(() => this.features()?.externalCalendarFeedEnabled === true);
    readonly inventoryQrEnabled = computed(() => this.features()?.inventoryQrEnabled === true);
    readonly notificationPreferencesEnabled = computed(() => this.features()?.notificationPreferencesEnabled === true);
    readonly webPushRemindersEnabled = computed(() => this.features()?.webPushRemindersEnabled === true);
    readonly eventPreparationEnabled = computed(() => this.features()?.eventPreparationEnabled === true);
    readonly current = this.features.asReadonly();
    readonly capabilities = this.capabilityState.asReadonly();

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

    isEnabled(feature: TenantFeature): boolean {
        switch (feature) {
            case TenantFeature.FINANCE:
                return this.financeEnabled();
            case TenantFeature.INVENTORY:
                return this.inventoryEnabled();
            case TenantFeature.ONBOARDING_IMPORT:
                return this.onboardingImportEnabled();
            case TenantFeature.EXTERNAL_CALENDAR_FEED:
                return this.externalCalendarFeedEnabled();
            case TenantFeature.INVENTORY_QR:
                return this.inventoryQrEnabled();
            case TenantFeature.NOTIFICATION_PREFERENCES:
                return this.notificationPreferencesEnabled();
            case TenantFeature.WEB_PUSH_REMINDERS:
                return this.webPushRemindersEnabled();
            case TenantFeature.EVENT_PREPARATION:
                return this.eventPreparationEnabled();
        }
    }

    loadCapabilities(): Observable<TenantFeatureCapabilities> {
        return this.http.get<TenantFeatureCapabilities>(`${environment.baseUrl}/tenant-features/capabilities`).pipe(tap((capabilities) => this.capabilityState.set(capabilities)));
    }
}
