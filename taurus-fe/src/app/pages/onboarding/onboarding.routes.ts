import { Routes } from '@angular/router';
import { RoleEnums } from '../../constants';
import { canActivateAuthRole } from '../../guard/auth-role.guard';
import { canDeactivateUnsavedChanges } from '../../guard/unsaved-changes.guard';
import { OnboardingComponent } from './onboarding.component';

export default [
    { path: '', component: OnboardingComponent, canActivate: [canActivateAuthRole], canDeactivate: [canDeactivateUnsavedChanges], data: { role: [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN] } },
    { path: 'imports/:id', component: OnboardingComponent, canActivate: [canActivateAuthRole], data: { role: [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN] } }
] as Routes;
