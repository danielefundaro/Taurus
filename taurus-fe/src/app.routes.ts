import { Routes } from '@angular/router';
import { canActivateAuthRole } from './app/guard/auth-role.guard';
import { AppLayout } from './app/layout/component/app.layout';
import { Dashboard } from './app/pages/dashboard/dashboard';
import { Documentation } from './app/pages/documentation/documentation';
import { Landing } from './app/pages/landing/landing';
import { Notfound } from './app/pages/notfound/notfound';
import { PreviewComponent } from './app/pages/preview/preview.component';

export const appRoutes: Routes = [
    {
        path: '',
        component: AppLayout,
        children: [
            { path: '', component: Dashboard },
            { path: 'uikit', loadChildren: () => import('./app/pages/uikit/uikit.routes') },
            { path: 'documentation', component: Documentation },
            { path: 'pages', loadChildren: () => import('./app/pages/pages.routes') },
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
    { path: 'auth', loadChildren: () => import('./app/pages/auth/auth.routes') },
    { path: '**', redirectTo: '/notfound' }
];
