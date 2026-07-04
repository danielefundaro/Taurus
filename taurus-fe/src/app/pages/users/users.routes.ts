import { Routes } from '@angular/router';
import { RoleEnums } from '../../constants';
import { canActivateAuthRole } from '../../guard/auth-role.guard';
import { canDeactivateUnsavedChanges } from '../../guard/unsaved-changes.guard';
import { DetailComponent } from './detail/detail.component';
import { UsersComponent } from './users.component';

export default [
    {
        path: '',
        component: UsersComponent,
        canActivate: [canActivateAuthRole],
        data: { role: [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN] },
    },
    {
        path: ':id',
        component: DetailComponent,
        canActivate: [canActivateAuthRole],
        canDeactivate: [canDeactivateUnsavedChanges],
        data: { role: [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN] },
    },
] as Routes;