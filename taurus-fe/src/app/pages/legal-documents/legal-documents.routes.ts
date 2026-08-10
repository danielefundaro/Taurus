import { Routes } from '@angular/router';
import { RoleEnums } from '../../constants';
import { canActivateAuthRole } from '../../guard/auth-role.guard';
import { LegalDocumentsComponent } from './legal-documents.component';

export default [
    {
        path: '',
        component: LegalDocumentsComponent,
        canActivate: [canActivateAuthRole],
        data: { role: [RoleEnums.SUPER_ADMIN] }
    }
] as Routes;
