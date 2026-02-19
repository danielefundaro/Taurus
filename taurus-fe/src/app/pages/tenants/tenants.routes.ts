import { Routes } from '@angular/router';
import { canActivateAuthRole } from '../../guard/auth-role.guard';
import { DetailComponent } from './detail/detail.component';
import { TenantsComponent } from './tenants.component';

export default [
    {
        path: '',
        component: TenantsComponent,
        canActivate: [canActivateAuthRole],
        data: { role: ['ROLE_SUPER_ADMIN'] },
    },
    {
        path: ':id',
        component: DetailComponent,
        canActivate: [canActivateAuthRole],
        data: { role: ['ROLE_SUPER_ADMIN'] },
    },
] as Routes;