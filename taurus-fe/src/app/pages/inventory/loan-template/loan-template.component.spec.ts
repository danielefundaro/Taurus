import { HttpContext } from '@angular/common/http';
import { of } from 'rxjs';
import { InventoryLoanTemplateOverview, InventoryLoanTemplateVersion } from '../../../module';
import { ConfirmService, InventoryService, ToastService } from '../../../service';
import { InventoryLoanTemplateComponent } from './loan-template.component';

describe('InventoryLoanTemplateComponent', () => {
    const version: InventoryLoanTemplateVersion = { id: 4, versionNumber: 2, origin: 'CUSTOM', fileName: 'modello banda.html', contentHash: 'a'.repeat(64), createdAt: '2026-10-09T10:00:00Z', active: false };
    const overview: InventoryLoanTemplateOverview = {
        defaultLabel: '2026-10-09',
        durations: { minMonths: 12, maxMonths: 24, renewalMaxMonths: 24, totalMaxMonths: 36 },
        settingsVersion: 3,
        versions: [version],
        variables: [],
        limits: { maxFileBytes: 2 * 1024 * 1024 }
    };

    function create(): {
        component: InventoryLoanTemplateComponent;
        inventoryService: jasmine.SpyObj<InventoryService>;
        toastService: jasmine.SpyObj<ToastService>;
        confirmService: jasmine.SpyObj<ConfirmService>;
    } {
        const inventoryService = jasmine.createSpyObj<InventoryService>('InventoryService', [
            'getLoanTemplate',
            'checkLoanTemplate',
            'previewLoanTemplate',
            'activateLoanTemplate',
            'useDefaultLoanTemplate',
            'updateLoanDurations',
            'downloadLoanTemplateVersion',
            'downloadDefaultLoanTemplate',
            'previewDefaultLoanTemplate'
        ]);
        inventoryService.getLoanTemplate.and.returnValue(of(overview));
        const toastService = jasmine.createSpyObj<ToastService>('ToastService', ['success', 'error']);
        const confirmService = jasmine.createSpyObj<ConfirmService>('ConfirmService', ['confirmReversible']);
        confirmService.confirmReversible.and.callFake((request) => request.accept());
        const component = new InventoryLoanTemplateComponent(inventoryService, toastService, confirmService);
        component.ngOnInit();
        return { component, inventoryService, toastService, confirmService };
    }

    function input(file: File): HTMLInputElement {
        const element = document.createElement('input');
        element.type = 'file';
        Object.defineProperty(element, 'files', { value: [file] });
        return element;
    }

    it('reads the chosen file, checks it and keeps preview and activation disabled with blocking issues', async () => {
        const { component, inventoryService } = create();
        inventoryService.checkLoanTemplate.and.returnValue(of({ valid: false, issues: [{ code: 'unknownVariable', args: { name: 'x' }, line: 3, blocking: true }] }));

        await component['selectFile'](input(new File(['<p>{{x}}</p>'], 'modello.html', { type: 'text/html' })));

        expect(inventoryService.checkLoanTemplate).toHaveBeenCalledWith({ fileName: 'modello.html', content: '<p>{{x}}</p>' });
        expect(component['blockingCount']).toBe(1);
        expect(component['issueMessage'](component['check']!.issues[0])).toBe('Variabile sconosciuta «x»');
        component['previewFile']();
        component['activate']();
        expect(inventoryService.previewLoanTemplate).not.toHaveBeenCalled();
        expect(inventoryService.activateLoanTemplate).not.toHaveBeenCalled();
    });

    it('activates a valid file after confirmation and shows the new version in use', async () => {
        const { component, inventoryService, confirmService } = create();
        const active = { ...version, id: 5, versionNumber: 3, active: true };
        inventoryService.checkLoanTemplate.and.returnValue(of({ valid: true, issues: [] }));
        inventoryService.activateLoanTemplate.and.returnValue(of({ ...overview, active, versions: [active, version] }));

        await component['selectFile'](input(new File(['<p>{{foglio.numero}}</p>'], 'nuovo.html')));
        component['activate']();

        expect(confirmService.confirmReversible).toHaveBeenCalled();
        expect(inventoryService.activateLoanTemplate).toHaveBeenCalledWith({ fileName: 'nuovo.html', content: '<p>{{foglio.numero}}</p>' }, jasmine.any(HttpContext));
        expect(component['activeLabel']).toBe('Modello caricato · versione 3');
        expect(component['file']).toBeUndefined();
    });

    it('refuses files over the size limit without sending them', async () => {
        const { component, inventoryService, toastService } = create();
        const large = new File(['x'], 'grande.html');
        Object.defineProperty(large, 'size', { value: overview.limits.maxFileBytes + 1 });

        await component['selectFile'](input(large));

        expect(toastService.error).toHaveBeenCalled();
        expect(inventoryService.checkLoanTemplate).not.toHaveBeenCalled();
    });

    it('saves the duration rules with the version of the settings', () => {
        const { component, inventoryService } = create();
        inventoryService.updateLoanDurations.and.returnValue(of({ ...overview, durations: { minMonths: 6, maxMonths: 24, renewalMaxMonths: 24, totalMaxMonths: 36 }, settingsVersion: 4 }));

        component['durations'].minMonths = 6;
        component.isDirty = true;
        component['saveDurations']();

        expect(inventoryService.updateLoanDurations).toHaveBeenCalledWith({ minMonths: 6, maxMonths: 24, renewalMaxMonths: 24, totalMaxMonths: 36 }, 3);
        expect(component.isDirty).toBeFalse();
        expect(component['overview']?.settingsVersion).toBe(4);
    });

    it('downloads a version with its file name and the default template with the Taurus name', () => {
        const { component, inventoryService } = create();
        inventoryService.downloadLoanTemplateVersion.and.returnValue(of(new Blob(['<html></html>'])));
        inventoryService.downloadDefaultLoanTemplate.and.returnValue(of(new Blob(['<html></html>'])));
        const anchors: HTMLAnchorElement[] = [];
        const createElement = document.createElement.bind(document);
        spyOn(document, 'createElement').and.callFake((tag: string) => {
            const element = createElement(tag);
            if (tag === 'a') {
                spyOn(element as HTMLAnchorElement, 'click');
                anchors.push(element as HTMLAnchorElement);
            }
            return element;
        });

        component['downloadVersion'](version);
        component['downloadActive']();

        expect(anchors.map((anchor) => anchor.download)).toEqual(['modello banda.html', 'foglio-affidamento-modello-predefinito.html']);
    });

    it('shows the variables with their Mustache syntax', () => {
        const { component } = create();

        expect(component['token']({ name: 'associazione.nome', type: 'TEXT', description: '', fields: [] })).toBe('{{associazione.nome}}');
        expect(component['token']({ name: 'beni', type: 'LIST', description: '', fields: [] })).toBe('{{#beni}}…{{/beni}}');
        expect(component['fieldToken']({ name: 'numero', type: 'TEXT', description: '', fields: [] })).toBe('{{numero}}');
    });
});
