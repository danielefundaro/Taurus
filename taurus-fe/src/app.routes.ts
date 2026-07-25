import { Routes } from '@angular/router';
import { RoleEnums } from './app/constants';
import { canActivateAuthRole } from './app/guard/auth-role.guard';
import { canDeactivateUnsavedChanges } from './app/guard/unsaved-changes.guard';
import { DashboardComponent } from './app/pages/dashboard/dashboard.component';
import { Forbidden } from './app/pages/forbidden/forbidden.component';
import { LayoutComponent } from './app/pages/layout/layout.component';
import { Notfound } from './app/pages/notfound/notfound.component';
import { PreviewComponent } from './app/pages/preview/preview.component';
import { ProfileComponent } from './app/pages/profile/profile.component';

export const appRoutes: Routes = [
    {
        path: '',
        component: LayoutComponent,
        children: [
            {
                path: '',
                component: DashboardComponent,
                canActivate: [canActivateAuthRole],
                data: { role: [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN, RoleEnums.ARCHIVIST, RoleEnums.USER, RoleEnums.USER_EXTERNAL] },
            },
            {
                path: 'tenants',
                loadChildren: () => import('./app/pages/tenants/tenants.routes'),
                canActivate: [canActivateAuthRole],
                data: { role: [RoleEnums.SUPER_ADMIN] },
            },
            {
                path: 'users',
                loadChildren: () => import('./app/pages/users/users.routes'),
                canActivate: [canActivateAuthRole],
                data: { role: [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN] },
            },
            {
                path: 'albums',
                loadChildren: () => import('./app/pages/albums/albums.routes'),
                canActivate: [canActivateAuthRole],
                data: { role: [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN, RoleEnums.ARCHIVIST, RoleEnums.USER, RoleEnums.USER_EXTERNAL] },
            },
            {
                path: 'tracks',
                loadChildren: () => import('./app/pages/tracks/tracks.routes'),
                canActivate: [canActivateAuthRole],
                data: { role: [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN, RoleEnums.ARCHIVIST, RoleEnums.USER, RoleEnums.USER_EXTERNAL] },
            },
            {
                path: 'instruments',
                loadChildren: () => import('./app/pages/instruments/instruments.routes'),
                canActivate: [canActivateAuthRole],
                data: { role: [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN, RoleEnums.ARCHIVIST] },
            },
            {
                path: 'calendar',
                loadChildren: () => import('./app/pages/calendar-events/calendar-events.routes'),
                canActivate: [canActivateAuthRole],
                data: { role: [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN, RoleEnums.ARCHIVIST, RoleEnums.USER, RoleEnums.USER_EXTERNAL] },
            },
            {
                path: 'preview',
                component: PreviewComponent,
                canActivate: [canActivateAuthRole],
                data: { role: [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN, RoleEnums.ARCHIVIST, RoleEnums.USER, RoleEnums.USER_EXTERNAL] },
            },
            {
                path: 'profile',
                component: ProfileComponent,
                canActivate: [canActivateAuthRole],
                canDeactivate: [canDeactivateUnsavedChanges],
                data: { role: [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN, RoleEnums.ARCHIVIST, RoleEnums.USER, RoleEnums.USER_EXTERNAL] },
            },
        ]
    },
    { path: 'notfound', component: Notfound },
    { path: 'forbidden', component: Forbidden },
    { path: '**', redirectTo: '/notfound' }
];
