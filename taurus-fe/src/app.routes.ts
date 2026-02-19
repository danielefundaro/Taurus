import { Routes } from '@angular/router';
import { canActivateAuthRole } from './app/guard/auth-role.guard';
import { Dashboard } from './app/pages/dashboard/dashboard';
import { Documentation } from './app/pages/documentation/documentation';
import { Landing } from './app/pages/landing/landing';
import { LayoutComponent } from './app/pages/layout/layout.component';
import { Notfound } from './app/pages/notfound/notfound.component';
import { PreviewComponent } from './app/pages/preview/preview.component';
import { Forbidden } from './app/pages/forbidden/forbidden.component';

export const appRoutes: Routes = [
    {
        path: '',
        component: LayoutComponent,
        children: [
            { path: '', component: Dashboard },
            { path: 'uikit', loadChildren: () => import('./app/pages/uikit/uikit.routes') },
            { path: 'documentation', component: Documentation },
            { path: 'pages', loadChildren: () => import('./app/pages/pages.routes') },
            {
                path: 'tenants',
                loadChildren: () => import('./app/pages/tenants/tenants.routes'),
                canActivate: [canActivateAuthRole],
                data: { role: ['ROLE_SUPER_ADMIN'] },
            },
            {
                path: 'users',
                loadChildren: () => import('./app/pages/users/users.routes'),
                canActivate: [canActivateAuthRole],
                data: { role: ['ROLE_ADMIN'] },
            },
            {
                path: 'albums',
                loadChildren: () => import('./app/pages/albums/albums.routes'),
                canActivate: [canActivateAuthRole],
                data: { role: ['ROLE_USER'] },
            },
            {
                path: 'tracks',
                loadChildren: () => import('./app/pages/tracks/tracks.routes'),
                canActivate: [canActivateAuthRole],
                data: { role: ['ROLE_USER'] },
            },
            {
                path: 'instruments',
                loadChildren: () => import('./app/pages/instruments/instruments.routes'),
                canActivate: [canActivateAuthRole],
                data: { role: ['ROLE_USER'] },
            },
            {
                path: 'preview',
                component: PreviewComponent,
                canActivate: [canActivateAuthRole],
                data: { role: ['ROLE_USER'] },
            },
        ]
    },
    { path: 'landing', component: Landing },
    { path: 'notfound', component: Notfound },
    { path: 'forbidden', component: Forbidden },
    { path: 'auth', loadChildren: () => import('./app/pages/auth/auth.routes') },
    { path: '**', redirectTo: '/notfound' }
];
