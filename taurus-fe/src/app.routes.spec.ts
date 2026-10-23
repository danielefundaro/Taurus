import { tenantFeatureGuard } from './app/guard/tenant-feature.guard';
import { RoleEnums } from './app/constants';
import { TenantFeature } from './app/module';
import calendarRoutes from './app/pages/calendar-events/calendar-events.routes';
import { appRoutes } from './app.routes';

describe('appRoutes tenant features', () => {
    const children = appRoutes.find((route) => route.path === '')?.children ?? [];

    it('protects Inventory and Finance with their feature flags', () => {
        const inventory = children.find((route) => route.path === 'inventory');
        const finance = children.find((route) => route.path === 'finance');

        expect(inventory?.canActivate).toContain(tenantFeatureGuard);
        expect(inventory?.data?.['feature']).toBe(TenantFeature.INVENTORY);
        expect(finance?.canActivate).toContain(tenantFeatureGuard);
        expect(finance?.data?.['feature']).toBe(TenantFeature.FINANCE);
    });

    it('does not couple unrelated routes to tenant features', () => {
        const albums = children.find((route) => route.path === 'albums');

        expect(albums?.canActivate).not.toContain(tenantFeatureGuard);
        expect(albums?.data?.['feature']).toBeUndefined();
    });
});

describe('appRoutes calendar', () => {
    const calendar = appRoutes.find((route) => route.path === '')?.children?.find((route) => route.path === 'calendar');

    it('lets the treasurer reach the detail of an event but not the calendar', () => {
        const list = calendarRoutes.find((route) => route.path === '');
        const detail = calendarRoutes.find((route) => route.path === ':id');

        expect(calendar?.data?.['role']).toContain(RoleEnums.TREASURER);
        expect(detail?.data?.['role']).toContain(RoleEnums.TREASURER);
        expect(list?.data?.['role']).not.toContain(RoleEnums.TREASURER);
    });
});
