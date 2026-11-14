import { provideHttpClient, withXhr } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../environments/environment';
import { TenantStatusService } from './tenant-status.service';

describe('TenantStatusService', () => {
    const url = `${environment.baseUrl}/tenant-status`;
    let service: TenantStatusService;
    let http: HttpTestingController;

    beforeEach(() => {
        TestBed.configureTestingModule({ providers: [provideHttpClient(withXhr()), provideHttpClientTesting()] });
        service = TestBed.inject(TenantStatusService);
        http = TestBed.inject(HttpTestingController);
    });

    afterEach(() => http.verify());

    it('enters bootstrap mode only once the backend reports a tenant without schema', async () => {
        expect(service.bootstrap()).toBeFalse();

        const result = firstValueFrom(service.load());
        http.expectOne(url).flush({ tenantCode: 'temp', provisioned: false });

        await expectAsync(result).toBeResolvedTo({ tenantCode: 'temp', provisioned: false });
        expect(service.bootstrap()).toBeTrue();
        expect(service.tenantCode()).toBe('temp');
    });

    it('shares one request and then serves the cached status', async () => {
        const first = firstValueFrom(service.load());
        const second = firstValueFrom(service.load());
        http.expectOne(url).flush({ tenantCode: 'acme', provisioned: true });
        await Promise.all([first, second]);

        await expectAsync(firstValueFrom(service.load())).toBeResolvedTo({ tenantCode: 'acme', provisioned: true });
        expect(service.bootstrap()).toBeFalse();
    });

    it('falls back to normal mode without caching when the status cannot be read', async () => {
        const failed = firstValueFrom(service.load());
        http.expectOne(url).flush(null, { status: 500, statusText: 'Server Error' });

        await expectAsync(failed).toBeResolvedTo({ tenantCode: null, provisioned: true });
        expect(service.bootstrap()).toBeFalse();

        const retried = firstValueFrom(service.load());
        http.expectOne(url).flush({ tenantCode: 'temp', provisioned: false });
        await retried;
        expect(service.bootstrap()).toBeTrue();
    });

    it('lets the tenant data through only for a tenant with schema', async () => {
        const provisioned = firstValueFrom(service.whenProvisioned());
        http.expectOne(url).flush({ tenantCode: 'acme', provisioned: true });
        await expectAsync(provisioned).toBeResolvedTo({ tenantCode: 'acme', provisioned: true });

        service.invalidate();
        let emitted = false;
        service.whenProvisioned().subscribe(() => (emitted = true));
        http.expectOne(url).flush({ tenantCode: 'temp', provisioned: false });
        expect(emitted).toBeFalse();
    });

    describe('on a public page', () => {
        const start = location.pathname + location.search;

        afterEach(() => history.replaceState(null, '', start));

        it('completes without reading the status, because the page has no session', () => {
            history.replaceState(null, '', '/legal/privacy');
            let emitted = false;
            let completed = false;

            service.whenProvisioned().subscribe({ next: () => (emitted = true), complete: () => (completed = true) });

            http.expectNone(url);
            expect(emitted).toBeFalse();
            expect(completed).toBeTrue();
        });
    });

    it('reads the status again after invalidation', async () => {
        const initial = firstValueFrom(service.load());
        http.expectOne(url).flush({ tenantCode: 'acme', provisioned: true });
        await initial;

        service.invalidate();
        const reloaded = firstValueFrom(service.load());
        http.expectOne(url).flush({ tenantCode: 'acme', provisioned: false });
        await reloaded;

        expect(service.bootstrap()).toBeTrue();
    });
});
