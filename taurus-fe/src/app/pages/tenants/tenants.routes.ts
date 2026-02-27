import { Routes } from '@angular/router';
import { RoleEnums } from '../../constants';
import { canActivateAuthRole } from '../../guard/auth-role.guard';
import { DetailComponent } from './detail/detail.component';
import { TenantsComponent } from './tenants.component';

export default [
    {
        path: '',
        component: TenantsComponent,
        canActivate: [canActivateAuthRole],
        data: { role: [RoleEnums.SUPER_ADMIN] },
    },
    {
        path: ':id',
        component: DetailComponent,
        canActivate: [canActivateAuthRole],
        data: { role: [RoleEnums.SUPER_ADMIN] },
    },
] as Routes;