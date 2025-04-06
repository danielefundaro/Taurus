import { inject } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivateFn, Router, RouterStateSnapshot, UrlTree } from '@angular/router';
import { AuthGuardData, createAuthGuard } from 'keycloak-angular';

const isAccessAllowed = async (
    route: ActivatedRouteSnapshot,
    _: RouterStateSnapshot,
    authData: AuthGuardData
): Promise<boolean | UrlTree> => {
    const { authenticated, grantedRoles } = authData;
    const requiredRole = route.data['role'];

    if (!requiredRole) {
        return false;
    }

    const hasRequiredRole = (roles: Array<string>): boolean =>
        Object.values(grantedRoles.resourceRoles['web_app']).some((role) => roles.some((r) => r === role));

    if (authenticated && hasRequiredRole(requiredRole)) {
        return true;
    }

    const router = inject(Router);
    return router.parseUrl('/forbidden');
};

export const canActivateAuthRole = createAuthGuard<CanActivateFn>(isAccessAllowed);
