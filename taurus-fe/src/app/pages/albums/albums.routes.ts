import { Routes } from '@angular/router';
import { canActivateAuthRole } from '../../guard/auth-role.guard';
import { AlbumsComponent } from './albums.component';
import { DetailComponent } from './detail/detail.component';
import { RoleEnums } from '../../constants';

export default [
    {
        path: '',
        component: AlbumsComponent,
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