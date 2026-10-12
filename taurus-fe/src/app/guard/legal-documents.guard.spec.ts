import { TestBed } from '@angular/core/testing';
import { Router, UrlTree } from '@angular/router';
import { firstValueFrom, Observable, of } from 'rxjs';
import { LegalStatus, TenantStatus } from '../module';
import { LegalService, TenantStatusService } from '../service';
import { legalDocumentsGuard } from './legal-documents.guard';

describe('legalDocumentsGuard', () => {
    let router: jasmine.SpyObj<Router>;
    let legalService: jasmine.SpyObj<LegalService>;
    let status: TenantStatus;

    beforeEach(() => {
        router = jasmine.createSpyObj<Router>('Router', ['createUrlTree']);
        legalService = jasmine.createSpyObj<LegalService>('LegalService', ['getStatus']);
        status = { tenantCode: 'acme', provisioned: true };
        TestBed.configureTestingModule({
            providers: [
                { provide: Router, useValue: router },
                { provide: LegalService, useValue: legalService },
                { provide: TenantStatusService, useValue: { load: () => of(status) } }
            ]
        });
    });

    function navigate(url: string): Promise<boolean | UrlTree> {
        const result = TestBed.runInInjectionContext(() => legalDocumentsGuard({} as never, { url } as never));
        return firstValueFrom(result as Observable<boolean | UrlTree>);
    }

    it('skips the acceptance check while the tenant has no schema', async () => {
        status = { tenantCode: 'temp', provisioned: false };

        await expectAsync(navigate('/tenants')).toBeResolvedTo(true);
        expect(legalService.getStatus).not.toHaveBeenCalled();
    });

    it('asks for the missing acceptances of a provisioned tenant', async () => {
        const acceptancePage = {} as UrlTree;
        router.createUrlTree.and.returnValue(acceptancePage);
        legalService.getStatus.and.returnValue(of({ compliant: false, documents: [] } as LegalStatus));

        await expectAsync(navigate('/albums')).toBeResolvedTo(acceptancePage);
        expect(router.createUrlTree).toHaveBeenCalledWith(['/legal/accept'], { queryParams: { returnUrl: '/albums' } });
    });

    it('lets compliant users through', async () => {
        legalService.getStatus.and.returnValue(of({ compliant: true, documents: [] } as LegalStatus));

        await expectAsync(navigate('/albums')).toBeResolvedTo(true);
    });
});
