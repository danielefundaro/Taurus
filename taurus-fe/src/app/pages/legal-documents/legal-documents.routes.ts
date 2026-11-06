import { Routes } from '@angular/router';
import { RoleEnums } from '../../constants';
import { canActivateAuthRole } from '../../guard/auth-role.guard';
import { canDeactivateUnsavedChanges } from '../../guard/unsaved-changes.guard';
import { LegalDocumentDetailComponent } from './detail/legal-document-detail.component';
import { LegalDocumentsComponent } from './legal-documents.component';

export default [
    {
        path: '',
        component: LegalDocumentsComponent,
        canActivate: [canActivateAuthRole],
        data: { role: [RoleEnums.SUPER_ADMIN] }
    },
    // A new draft, empty or copied from another version (`?from=<id>`).
    {
        path: 'new',
        component: LegalDocumentDetailComponent,
        canActivate: [canActivateAuthRole],
        canDeactivate: [canDeactivateUnsavedChanges],
        data: { role: [RoleEnums.SUPER_ADMIN] }
    },
    {
        path: ':id',
        component: LegalDocumentDetailComponent,
        canActivate: [canActivateAuthRole],
        canDeactivate: [canDeactivateUnsavedChanges],
        data: { role: [RoleEnums.SUPER_ADMIN] }
    }
] as Routes;
