import { tenantFeatureGuard } from './app/guard/tenant-feature.guard';
import { TenantFeature } from './app/module';
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
