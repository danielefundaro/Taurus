import { inject } from '@angular/core';
import { CanActivateChildFn, Router } from '@angular/router';
import { map } from 'rxjs';
import { RoleEnums } from '../constants';
import { KeycloakService, TenantStatusService } from '../service';

/**
 * In modalità bootstrap il tenant della sessione non ha dati: il super amministratore può solo gestire i tenant,
 * gli altri utenti vedono la pagina che spiega che il tenant non è ancora configurato.
 */
export const tenantBootstrapGuard: CanActivateChildFn = (_, state) => {
    const tenantStatus = inject(TenantStatusService);
    const keycloakService = inject(KeycloakService);
    const router = inject(Router);

    return tenantStatus.load().pipe(
        map((status) => {
            if (status.provisioned) return true;
            if (keycloakService.currentUserRole !== RoleEnums.SUPER_ADMIN) return router.createUrlTree(['/tenant-unavailable']);
            return isTenantManagement(state.url) ? true : router.createUrlTree(['/tenants']);
        })
    );
};

function isTenantManagement(url: string): boolean {
    const path = url.split(/[?#]/)[0];
    return path === '/tenants' || path.startsWith('/tenants/');
}
