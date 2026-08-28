import { Routes } from '@angular/router';
import { canActivateAuthRole } from '../../guard/auth-role.guard';
import { canDeactivateUnsavedChanges } from '../../guard/unsaved-changes.guard';
import { AlbumsComponent } from './albums.component';
import { DetailComponent } from './detail/detail.component';
import { RoleEnums } from '../../constants';

export default [
    {
        path: '',
        component: AlbumsComponent,
        canActivate: [canActivateAuthRole],
        data: { role: [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN, RoleEnums.ARCHIVIST, RoleEnums.USER, RoleEnums.USER_EXTERNAL] },
    },
    {
        path: ':id',
        component: DetailComponent,
        canActivate: [canActivateAuthRole],
        canDeactivate: [canDeactivateUnsavedChanges],
        data: { role: [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN, RoleEnums.ARCHIVIST, RoleEnums.USER, RoleEnums.USER_EXTERNAL] },
    },
] as Routes;