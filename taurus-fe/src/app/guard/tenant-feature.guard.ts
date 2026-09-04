import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { TenantFeature } from '../module';
import { TenantFeatureService } from '../service';

export const tenantFeatureGuard: CanActivateFn = (route) => {
    const service = inject(TenantFeatureService);
    const router = inject(Router);
    const feature = route.data['feature'] as TenantFeature;
    return service.refresh().pipe(
        map(() => {
            const enabled = feature === TenantFeature.FINANCE ? service.financeEnabled() : service.inventoryEnabled();
            return enabled ? true : router.createUrlTree(['/']);
        }),
        catchError(() => of(router.createUrlTree(['/'])))
    );
};
