import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { firstValueFrom, Observable, of } from 'rxjs';
import { TenantFeature } from '../module';
import { TenantFeatureService } from '../service';
import { tenantFeatureGuard } from './tenant-feature.guard';

describe('tenantFeatureGuard', () => {
    let router: jasmine.SpyObj<Router>;
    let service: {
        refresh: jasmine.Spy;
        financeEnabled: jasmine.Spy;
        inventoryEnabled: jasmine.Spy;
    };

    beforeEach(() => {
        router = jasmine.createSpyObj<Router>('Router', ['createUrlTree']);
        service = {
            refresh: jasmine.createSpy().and.returnValue(of({})),
            financeEnabled: jasmine.createSpy().and.returnValue(false),
            inventoryEnabled: jasmine.createSpy().and.returnValue(true)
        };
        TestBed.configureTestingModule({
            providers: [
                { provide: Router, useValue: router },
                { provide: TenantFeatureService, useValue: service }
            ]
        });
    });

    it('redirects when the requested feature is disabled', async () => {
        const redirect = {} as UrlTree;
        router.createUrlTree.and.returnValue(redirect);

        const result = TestBed.runInInjectionContext(() =>
            tenantFeatureGuard({ data: { feature: TenantFeature.FINANCE } } as never, {} as never)
        );

        await expectAsync(firstValueFrom(result as Observable<boolean | UrlTree>)).toBeResolvedTo(redirect);
        expect(service.refresh).toHaveBeenCalled();
        expect(router.createUrlTree).toHaveBeenCalledWith(['/']);
    });

    it('allows navigation when the requested feature is enabled', async () => {
        const result = TestBed.runInInjectionContext(() =>
            tenantFeatureGuard({ data: { feature: TenantFeature.INVENTORY } } as never, {} as never)
        );

        await expectAsync(firstValueFrom(result as Observable<boolean | UrlTree>)).toBeResolvedTo(true);
        expect(router.createUrlTree).not.toHaveBeenCalled();
    });
});
