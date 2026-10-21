import { ActivatedRoute, Router } from '@angular/router';
import { TenantFeature, Tenants } from '../../../module';
import { DateConverterPipe } from '../../../pipe';
import { ConfirmService, KeycloakService, TenantFeatureService, TenantsService, ToastService } from '../../../service';
import { DetailComponent } from './detail.component';

describe('Tenant DetailComponent', () => {
    function create(): { component: DetailComponent; confirmService: jasmine.SpyObj<ConfirmService> } {
        const confirmService = jasmine.createSpyObj<ConfirmService>('ConfirmService', ['confirmReversible', 'confirmDestructive']);
        const component = new DetailComponent(
            {} as TenantsService,
            {} as ToastService,
            {} as DateConverterPipe,
            {} as ActivatedRoute,
            {} as Router,
            confirmService,
            {} as KeycloakService,
            { capabilities: () => null } as unknown as TenantFeatureService
        );
        return { component, confirmService };
    }

    function tenant(values: Partial<Tenants>): Tenants {
        return Object.assign(new Tenants(), values);
    }

    it('lists the electronic signature among the features that a disabled loan sheet turns off', () => {
        const { component, confirmService } = create();
        component['originalFeatureValues'] = { inventoryEnabled: true, inventoryLoanSheetEnabled: true, inventorySignatureEnabled: true };
        component.tenant = tenant({ inventoryEnabled: true, inventoryLoanSheetEnabled: false, inventorySignatureEnabled: true });

        component.save();

        expect(confirmService.confirmReversible).toHaveBeenCalledWith(jasmine.objectContaining({ consequence: jasmine.stringContaining('Fogli di affidamento e Firma elettronica (per dipendenza)') }));
    });

    it('follows the chain of dependencies from the inventory to the electronic signature', () => {
        const { component, confirmService } = create();
        component['originalFeatureValues'] = { inventoryEnabled: true, inventoryLoanSheetEnabled: true, inventorySignatureEnabled: true };
        component.tenant = tenant({ inventoryEnabled: false, inventoryLoanSheetEnabled: true, inventorySignatureEnabled: true });

        component.save();

        const consequence = confirmService.confirmReversible.calls.mostRecent().args[0].consequence;
        expect(consequence).toContain('Inventario');
        expect(consequence).toContain('Fogli di affidamento (per dipendenza)');
        expect(consequence).toContain('Firma elettronica (per dipendenza)');

        const signature = component['tenantFeatures'].find((item) => item.feature === TenantFeature.INVENTORY_SIGNATURE)!;
        expect(component['featureStatus'](signature)).toBe('Richiede un’altra funzionalità');
        component.tenant.inventoryEnabled = true;
        expect(component['featureStatus'](signature)).toBe('Disponibile');
    });
});
