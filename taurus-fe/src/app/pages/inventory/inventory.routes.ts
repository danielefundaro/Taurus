import { Routes } from '@angular/router';
import { RoleEnums } from '../../constants';
import { canActivateAuthRole } from '../../guard/auth-role.guard';
import { canDeactivateUnsavedChanges } from '../../guard/unsaved-changes.guard';
import { InventoryComponent } from './inventory.component';
import { InventoryDetailComponent } from './detail/detail.component';
import { InventoryAssignmentDetailComponent } from './assignment-detail/assignment-detail.component';

export default [
    {
        path: '',
        component: InventoryComponent,
        canActivate: [canActivateAuthRole],
        data: { role: [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN, RoleEnums.ARCHIVIST, RoleEnums.USER, RoleEnums.USER_EXTERNAL] }
    },
    {
        path: 'items/:id',
        component: InventoryDetailComponent,
        canActivate: [canActivateAuthRole],
        canDeactivate: [canDeactivateUnsavedChanges],
        data: { role: [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN] }
    },
    {
        path: 'assignments/:id',
        component: InventoryAssignmentDetailComponent,
        canActivate: [canActivateAuthRole],
        data: { role: [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN, RoleEnums.ARCHIVIST, RoleEnums.USER, RoleEnums.USER_EXTERNAL] }
    },
    {
        path: ':id',
        component: InventoryDetailComponent,
        canActivate: [canActivateAuthRole],
        canDeactivate: [canDeactivateUnsavedChanges],
        data: { role: [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN] }
    }
] as Routes;
