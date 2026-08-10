import { inject } from '@angular/core';
import { CanActivateChildFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { LegalService } from '../service';

export const legalDocumentsGuard: CanActivateChildFn = (_, state) => {
    const legalService = inject(LegalService);
    const router = inject(Router);
    const acceptancePage = () =>
        router.createUrlTree(['/legal/accept'], {
            queryParams: { returnUrl: state.url }
        });

    return legalService.getStatus().pipe(
        map((status) => (status.compliant ? true : acceptancePage())),
        catchError(() => of(acceptancePage()))
    );
};
