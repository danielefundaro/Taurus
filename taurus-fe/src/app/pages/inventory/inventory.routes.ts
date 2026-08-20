import { Routes } from '@angular/router';
import { RoleEnums } from '../../constants';
import { canActivateAuthRole } from '../../guard/auth-role.guard';
import { InventoryComponent } from './inventory.component';

export default [{
    path: '',
    component: InventoryComponent,
    canActivate: [canActivateAuthRole],
    data: { role: [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN] },
}] as Routes;
