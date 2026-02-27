import { Routes } from '@angular/router';
import { RoleEnums } from '../../constants';
import { canActivateAuthRole } from '../../guard/auth-role.guard';
import { DetailComponent } from './detail/detail.component';
import { TracksComponent } from './tracks.component';

export default [
    {
        path: '',
        component: TracksComponent,
        canActivate: [canActivateAuthRole],
        data: { role: [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN, RoleEnums.ARCHIVIST, RoleEnums.USER] },
    },
    {
        path: ':id',
        component: DetailComponent,
        canActivate: [canActivateAuthRole],
        data: { role: [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN, RoleEnums.ARCHIVIST, RoleEnums.USER] },
    },
] as Routes;