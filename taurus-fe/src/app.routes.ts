import { Routes } from '@angular/router';
import { RoleEnums } from './app/constants';
import { canActivateAuthRole, canDeactivateUnsavedChanges, legalDocumentsGuard, tenantFeatureGuard } from './app/guard';
import { TenantFeature } from './app/module';
import { LayoutComponent } from './app/pages/layout/layout.component';

export const appRoutes: Routes = [
    { path: 'legal/accept', loadComponent: () => import('./app/pages/legal-acceptance/legal-acceptance.component').then((module) => module.LegalAcceptanceComponent) },
    {
        path: '',
        component: LayoutComponent,
        canActivateChild: [legalDocumentsGuard],
        children: [
            {
                path: '',
                loadComponent: () => import('./app/pages/dashboard/dashboard.component').then((module) => module.DashboardComponent),
                canActivate: [canActivateAuthRole],
                data: { role: [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN, RoleEnums.TREASURER, RoleEnums.ARCHIVIST, RoleEnums.USER, RoleEnums.USER_EXTERNAL] }
            },
            {
                path: 'tenants',
                loadChildren: () => import('./app/pages/tenants/tenants.routes'),
                canActivate: [canActivateAuthRole],
                data: { role: [RoleEnums.SUPER_ADMIN] }
            },
            {
                path: 'users',
                loadChildren: () => import('./app/pages/users/users.routes'),
                canActivate: [canActivateAuthRole],
                data: { role: [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN] }
            },
            {
                path: 'onboarding',
                loadChildren: () => import('./app/pages/onboarding/onboarding.routes'),
                canActivate: [canActivateAuthRole, tenantFeatureGuard],
                data: { role: [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN], feature: TenantFeature.ONBOARDING_IMPORT }
            },
            {
                path: 'legal-documents',
                loadChildren: () => import('./app/pages/legal-documents/legal-documents.routes'),
                canActivate: [canActivateAuthRole],
                data: { role: [RoleEnums.SUPER_ADMIN] }
            },
            {
                path: 'albums',
                loadChildren: () => import('./app/pages/albums/albums.routes'),
                canActivate: [canActivateAuthRole],
                data: { role: [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN, RoleEnums.ARCHIVIST, RoleEnums.USER, RoleEnums.USER_EXTERNAL] }
            },
            {
                path: 'tracks',
                loadChildren: () => import('./app/pages/tracks/tracks.routes'),
                canActivate: [canActivateAuthRole],
                data: { role: [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN, RoleEnums.ARCHIVIST, RoleEnums.USER, RoleEnums.USER_EXTERNAL] }
            },
            {
                path: 'instruments',
                loadChildren: () => import('./app/pages/instruments/instruments.routes'),
                canActivate: [canActivateAuthRole],
                data: { role: [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN, RoleEnums.ARCHIVIST] }
            },
            {
                path: 'inventory',
                loadChildren: () => import('./app/pages/inventory/inventory.routes'),
                canActivate: [canActivateAuthRole, tenantFeatureGuard],
                data: { role: [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN, RoleEnums.ARCHIVIST, RoleEnums.USER, RoleEnums.USER_EXTERNAL], feature: TenantFeature.INVENTORY }
            },
            {
                path: 'finance',
                loadChildren: () => import('./app/pages/finance/finance.routes'),
                canActivate: [canActivateAuthRole, tenantFeatureGuard],
                data: { role: [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN, RoleEnums.TREASURER], feature: TenantFeature.FINANCE }
            },
            {
                path: 'calendar',
                loadChildren: () => import('./app/pages/calendar-events/calendar-events.routes'),
                canActivate: [canActivateAuthRole],
                data: { role: [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN, RoleEnums.ARCHIVIST, RoleEnums.USER, RoleEnums.USER_EXTERNAL] }
            },
            {
                path: 'admin/notification-delivery',
                loadComponent: () => import('./app/pages/admin/notification-delivery/notification-delivery.component').then((module) => module.NotificationDeliveryComponent),
                canActivate: [canActivateAuthRole],
                data: { role: [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN] }
            },
            {
                path: 'admin/calendar-feeds',
                loadComponent: () => import('./app/pages/admin/calendar-feeds/calendar-feeds.component').then((module) => module.CalendarFeedsComponent),
                canActivate: [canActivateAuthRole, tenantFeatureGuard],
                data: { role: [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN], feature: TenantFeature.EXTERNAL_CALENDAR_FEED }
            },
            {
                path: 'preview',
                loadComponent: () => import('./app/pages/preview/preview.component').then((module) => module.PreviewComponent),
                canActivate: [canActivateAuthRole],
                data: { role: [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN, RoleEnums.ARCHIVIST, RoleEnums.USER, RoleEnums.USER_EXTERNAL] }
            },
            {
                path: 'profile',
                loadComponent: () => import('./app/pages/profile/profile.component').then((module) => module.ProfileComponent),
                canActivate: [canActivateAuthRole],
                canDeactivate: [canDeactivateUnsavedChanges],
                data: { role: [RoleEnums.SUPER_ADMIN, RoleEnums.ADMIN, RoleEnums.TREASURER, RoleEnums.ARCHIVIST, RoleEnums.USER, RoleEnums.USER_EXTERNAL] }
            }
        ]
    },
    { path: 'notfound', loadComponent: () => import('./app/pages/notfound/notfound.component').then((module) => module.Notfound) },
    { path: 'forbidden', loadComponent: () => import('./app/pages/forbidden/forbidden.component').then((module) => module.Forbidden) },
    { path: '**', redirectTo: '/notfound' }
];
