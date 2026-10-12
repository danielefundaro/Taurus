import { inject } from '@angular/core';
import { CanActivateChildFn, Router } from '@angular/router';
import { catchError, map, of, switchMap } from 'rxjs';
import { LegalService, TenantStatusService } from '../service';

export const legalDocumentsGuard: CanActivateChildFn = (_, state) => {
    const legalService = inject(LegalService);
    const tenantStatus = inject(TenantStatusService);
    const router = inject(Router);
    const acceptancePage = () =>
        router.createUrlTree(['/legal/accept'], {
            queryParams: { returnUrl: state.url }
        });

    // Le accettazioni vivono nello schema del tenant: in modalità bootstrap non esistono ancora.
    return tenantStatus.load().pipe(
        switchMap((status) =>
            status.provisioned
                ? legalService.getStatus().pipe(
                      map((legalStatus) => (legalStatus.compliant ? true : acceptancePage())),
                      catchError(() => of(acceptancePage()))
                  )
                : of(true)
        )
    );
};
