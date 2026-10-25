import { ActivatedRoute, Router } from '@angular/router';
import { TenantFeature, Tenants } from '../../../module';
import { DateConverterPipe } from '../../../pipe';
import { ConfirmService, KeycloakService, TenantFeatureService, TenantsService, ToastService } from '../../../service';
import { DetailComponent } from './detail.component';

describe('Tenant DetailComponent', () => {
    function create(capabilities: unknown = null): { component: DetailComponent; confirmService: jasmine.SpyObj<ConfirmService> } {
        const confirmService = jasmine.createSpyObj<ConfirmService>('ConfirmService', ['confirmReversible', 'confirmDestructive']);
        const component = new DetailComponent(
            {} as TenantsService,
            {} as ToastService,
            {} as DateConverterPipe,
            {} as ActivatedRoute,
            {} as Router,
            confirmService,
            {} as KeycloakService,
            { capabilities: () => capabilities } as unknown as TenantFeatureService
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

    it('splits the flags into main modules and the extensions that depend on them', () => {
        const { component } = create();

        const [main, extension] = component['featureGroups'];
        const modules = main.items.map((item) => item.label);
        const extensions = extension.items.map((item) => item.label);

        expect(main.heading).toBe('Moduli principali');
        expect(extension.heading).toBe('Estensioni');

        expect(modules).toContain('Inventario');
        expect(modules).toContain('Preferenze notifiche');
        expect(extensions).toEqual(['QR code inventario', 'Fogli di affidamento', 'Firma elettronica', 'Promemoria eventi Web Push']);
        expect(modules.length + extensions.length).toBe(component['tenantFeatures'].length);
    });

    it('explains that a flag saved without the installation capability is a planned enablement', () => {
        const unavailable = { [TenantFeature.SCORE_PLAYBACK]: { available: false, dependencies: [] } };
        const { component } = create(unavailable);
        const playback = component['tenantFeatures'].find((item) => item.feature === TenantFeature.SCORE_PLAYBACK)!;
        component.tenant = tenant({ scorePlaybackEnabled: true });

        expect(component['featureStatus'](playback)).toBe('Non disponibile nell’installazione');
        expect(component['featureHint'](playback)).toContain('Abilitazione pianificata');

        component.tenant.scorePlaybackEnabled = false;
        expect(component['featureHint'](playback)).toContain('Il valore viene salvato comunque');
    });

    it('gives no hint when the installation offers the feature', () => {
        const { component } = create({ [TenantFeature.SCORE_PLAYBACK]: { available: true, dependencies: [] } });
        const playback = component['tenantFeatures'].find((item) => item.feature === TenantFeature.SCORE_PLAYBACK)!;
        component.tenant = tenant({ scorePlaybackEnabled: true });

        expect(component['featureHint'](playback)).toBeUndefined();
    });
});