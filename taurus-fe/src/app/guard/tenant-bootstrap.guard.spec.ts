import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { firstValueFrom, Observable, of } from 'rxjs';
import { RoleEnums } from '../constants';
import { TenantStatus } from '../module';
import { KeycloakService, TenantStatusService } from '../service';
import { tenantBootstrapGuard } from './tenant-bootstrap.guard';

describe('tenantBootstrapGuard', () => {
    let router: jasmine.SpyObj<Router>;
    let status: TenantStatus;
    let role: RoleEnums;

    beforeEach(() => {
        router = jasmine.createSpyObj<Router>('Router', ['createUrlTree']);
        router.createUrlTree.and.callFake((commands: readonly unknown[]) => ({ commands }) as unknown as UrlTree);
        status = { tenantCode: 'temp', provisioned: false };
        role = RoleEnums.SUPER_ADMIN;
        TestBed.configureTestingModule({
            providers: [
                { provide: Router, useValue: router },
                { provide: TenantStatusService, useValue: { load: () => of(status) } },
                {
                    provide: KeycloakService,
                    useValue: {
                        get currentUserRole() {
                            return role;
                        }
                    }
                }
            ]
        });
    });

    function navigate(url: string): Promise<boolean | UrlTree> {
        const result = TestBed.runInInjectionContext(() => tenantBootstrapGuard({} as never, { url } as never));
        return firstValueFrom(result as Observable<boolean | UrlTree>);
    }

    it('lets every route through once the tenant has a schema', async () => {
        status = { tenantCode: 'acme', provisioned: true };

        await expectAsync(navigate('/finance')).toBeResolvedTo(true);
        expect(router.createUrlTree).not.toHaveBeenCalled();
    });

    it('keeps tenant management available to the super administrator', async () => {
        await expectAsync(navigate('/tenants')).toBeResolvedTo(true);
        await expectAsync(navigate('/tenants/new?copy=1')).toBeResolvedTo(true);
    });

    it('sends the super administrator to tenant management from any other page', async () => {
        await expectAsync(navigate('/')).toBeResolvedTo({ commands: ['/tenants'] } as unknown as UrlTree);
        await expectAsync(navigate('/tenants-archive')).toBeResolvedTo({ commands: ['/tenants'] } as unknown as UrlTree);
    });

    it('explains to other users that the tenant is not configured', async () => {
        role = RoleEnums.ADMIN;

        await expectAsync(navigate('/tenants')).toBeResolvedTo({ commands: ['/tenant-unavailable'] } as unknown as UrlTree);
    });
});
