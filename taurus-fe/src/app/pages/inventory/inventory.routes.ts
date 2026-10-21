import { Routes } from '@angular/router';
import { RoleEnums } from '../../constants';
import { tenantFeatureGuard } from '../../guard/tenant-feature.guard';
import { TenantFeature } from '../../module';
import { canActivateAuthRole } from '../../guard/auth-role.guard';
import { canDeactivateUnsavedChanges } from '../../guard/unsaved-changes.guard';
import { InventoryComponent } from './inventory.component';
import { InventoryDetailComponent } from './detail/detail.component';
import { InventoryAssignmentDetailComponent } from './assignment-detail/assignment-detail.component';
import { InventoryScanComponent } from './scan/inventory-scan.component';
import { InventoryLoanTemplateComponent } from './loan-template/loan-template.component';

export default [
    {
        path: 'scan/v1/:publicId',
        component: InventoryScanComponent,
        canActivate: [canActivateAuthRole],
        data: { role: [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN, RoleEnums.ARCHIVIST, RoleEnums.USER, RoleEnums.USER_EXTERNAL] }
    },
    {
        path: '',
        component: InventoryComponent,
        canActivate: [canActivateAuthRole],
        data: { role: [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN, RoleEnums.ARCHIVIST, RoleEnums.USER, RoleEnums.USER_EXTERNAL] }
    },
    {
        path: 'loan-template',
        component: InventoryLoanTemplateComponent,
        canActivate: [canActivateAuthRole, tenantFeatureGuard],
        canDeactivate: [canDeactivateUnsavedChanges],
        data: { role: [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN], feature: TenantFeature.INVENTORY_LOAN_SHEET }
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
