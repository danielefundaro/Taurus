import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';
import { TenantFeatureService } from './tenant-feature.service';

describe('TenantFeatureService', () => {
    let service: TenantFeatureService;
    let http: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
        service = TestBed.inject(TenantFeatureService);
        http = TestBed.inject(HttpTestingController);
    });

    afterEach(() => http.verify());

    it('keeps features hidden until the first response and exposes the result', async () => {
        expect(service.loaded()).toBeFalse();
        expect(service.financeEnabled()).toBeFalse();
        expect(service.inventoryEnabled()).toBeFalse();

        const result = firstValueFrom(service.refresh());
        http.expectOne(`${environment.baseUrl}/tenant-features/current`).flush({
            tenantCode: 'A',
            version: 4,
            financeEnabled: true,
            inventoryEnabled: false
        });

        await result;
        expect(service.loaded()).toBeTrue();
        expect(service.financeEnabled()).toBeTrue();
        expect(service.inventoryEnabled()).toBeFalse();
    });

    it('shares concurrent refreshes and supports a forced refresh', async () => {
        const first = firstValueFrom(service.refresh());
        const concurrent = firstValueFrom(service.refresh());
        http.expectOne(`${environment.baseUrl}/tenant-features/current`).flush({
            tenantCode: 'A',
            version: 1,
            financeEnabled: true,
            inventoryEnabled: true
        });
        await Promise.all([first, concurrent]);

        await firstValueFrom(service.refresh());
        http.expectNone(`${environment.baseUrl}/tenant-features/current`);

        const forced = firstValueFrom(service.refresh(true));
        http.expectOne(`${environment.baseUrl}/tenant-features/current`).flush({
            tenantCode: 'A',
            version: 2,
            financeEnabled: false,
            inventoryEnabled: true
        });
        await forced;
        expect(service.financeEnabled()).toBeFalse();
    });
});
