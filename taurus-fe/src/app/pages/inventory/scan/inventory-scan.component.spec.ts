import { HttpErrorResponse } from '@angular/common/http';
import { Meta } from '@angular/platform-browser';
import { ActivatedRoute, convertToParamMap, Router } from '@angular/router';
import { DialogService } from 'primeng/dynamicdialog';
import { of, Subject, throwError } from 'rxjs';
import { InventoryScanAssignment, InventoryScanResult } from '../../../module';
import { InventoryScanService, InventoryService, KeycloakService, ToastService, UserInventoryService } from '../../../service';
import { InventoryScanComponent } from './inventory-scan.component';

describe('InventoryScanComponent', () => {
    const admin: InventoryScanResult = {
        target: 'ADMIN_ITEM',
        itemId: 42,
        inventoryNumber: 'LEG-0012',
        name: 'Leggio pieghevole',
        conditionStatus: 'FAIR',
        totalQuantity: 5,
        assignedQuantity: 3,
        availableQuantity: 2,
        openIssueCount: 1,
        unsafe: false,
        overdueAssignments: 1,
        expiringAssignments: 2,
        expiringWithinDays: 30,
        pendingDecisions: 1,
        allowedActions: ['ASSIGN', 'RETURN', 'ADD_ITEM_PHOTO', 'REPORT_ISSUE', 'PRINT_LABEL', 'ROTATE_CODE'],
        assignments: []
    };

    function assignment(id: number, allowedActions: InventoryScanAssignment['allowedActions']): InventoryScanAssignment {
        return {
            assignmentId: id,
            inventoryNumber: 'LEG-0012',
            itemName: 'Leggio pieghevole',
            conditionStatus: 'GOOD',
            outstandingQuantity: 1,
            status: 'ACTIVE',
            expirationDate: '2026-11-01',
            decisionPending: allowedActions.includes('COMPLETE_DECISION'),
            allowedActions
        };
    }

    function create(result: InventoryScanResult | Error, dialogResult?: unknown) {
        const scanService = jasmine.createSpyObj<InventoryScanService>('InventoryScanService', ['resolve']);
        scanService.resolve.and.callFake(() => (result instanceof Error ? throwError(() => new HttpErrorResponse({ status: 404 })) : of(result)));
        const inventoryService = jasmine.createSpyObj<InventoryService>('InventoryService', ['photoUrl', 'uploadPhoto', 'rotateQrCode', 'generateLabels']);
        const userInventoryService = jasmine.createSpyObj<UserInventoryService>('UserInventoryService', ['photoUrl']);
        const router = jasmine.createSpyObj<Router>('Router', ['navigate']);
        const toastService = jasmine.createSpyObj<ToastService>('ToastService', ['success', 'error', 'warn']);
        const dialogService = jasmine.createSpyObj<DialogService>('DialogService', ['open']);
        dialogService.open.and.returnValue({ onClose: of(dialogResult) } as never);
        const component = new InventoryScanComponent(
            { snapshot: { paramMap: convertToParamMap({ publicId: '550e8400-e29b-41d4-a716-446655440000' }) } } as unknown as ActivatedRoute,
            router,
            scanService,
            inventoryService,
            userInventoryService,
            { currentUserTenantCode: 'banda' } as KeycloakService,
            dialogService,
            toastService,
            jasmine.createSpyObj<Meta>('Meta', ['updateTag'])
        );
        return { component, scanService, inventoryService, router, toastService, dialogService };
    }

    it('shows the administrative summary and offers only the actions the backend allows', () => {
        const { component } = create({ ...admin, allowedActions: ['RETURN', 'PRINT_LABEL'] });

        component.ngOnInit();

        expect(component['result']?.itemId).toBe(42);
        expect(component['can']('RETURN')).toBeTrue();
        expect(component['can']('ASSIGN')).toBeFalse();
        expect(component['can']('ROTATE_CODE')).toBeFalse();
    });

    it('shows nothing about the item when the code does not resolve', () => {
        const { component } = create(new Error('not found'));

        component.ngOnInit();

        expect(component['unavailable']).toBeTrue();
        expect(component['result']).toBeUndefined();
    });

    it('opens the only relevant assignment directly, replacing the scan in the history', () => {
        const { component, router } = create({ target: 'OWN_ASSIGNMENTS', allowedActions: [], assignments: [assignment(7, ['VIEW'])] });

        component.ngOnInit();

        expect(router.navigate).toHaveBeenCalledOnceWith(['/inventory/assignments', 7], { replaceUrl: true });
        expect(component['result']).toBeUndefined();
    });

    it('lists several assignments with links to the sections that complete each action', () => {
        const { component, router } = create({
            target: 'OWN_ASSIGNMENTS',
            allowedActions: [],
            assignments: [assignment(7, ['VIEW', 'COMPLETE_DECISION', 'REQUEST_RETURN', 'REPORT_ISSUE', 'ADD_ISSUE_PHOTO']), assignment(8, ['VIEW', 'ADD_RETURN_PHOTO'])]
        });

        component.ngOnInit();

        expect(router.navigate).not.toHaveBeenCalled();
        const [first, second] = component['result']!.assignments;
        expect(component['assignmentLinks'](first).map((link) => link.fragment)).toEqual(['presa-visione', 'riconsegna', 'segnalazioni']);
        expect(component['assignmentLinks'](second).map((link) => link.label)).toEqual(['Foto di riconsegna']);
    });

    it('uploads a valid photo of the item and refreshes the summary', () => {
        const { component, scanService, inventoryService, toastService } = create(admin);
        inventoryService.uploadPhoto.and.returnValue(of({}));
        component.ngOnInit();
        const file = new File(['x'], 'leggio.jpg', { type: 'image/jpeg' });
        const input = document.createElement('input');
        Object.defineProperty(input, 'files', { value: [file] });

        component['uploadItemPhoto']({ target: input } as unknown as Event);

        expect(inventoryService.uploadPhoto).toHaveBeenCalledOnceWith(42, file);
        expect(toastService.success).toHaveBeenCalled();
        expect(scanService.resolve).toHaveBeenCalledTimes(2);
    });

    it('rejects a photo in another format without uploading it', () => {
        const { component, inventoryService, toastService } = create(admin);
        component.ngOnInit();
        const input = document.createElement('input');
        Object.defineProperty(input, 'files', { value: [new File(['x'], 'leggio.webp', { type: 'image/webp' })] });

        component['uploadItemPhoto']({ target: input } as unknown as Event);

        expect(inventoryService.uploadPhoto).not.toHaveBeenCalled();
        expect(toastService.error).toHaveBeenCalled();
    });

    it('keeps the summary after a rotation and asks for the new label, since the scanned code no longer resolves', () => {
        const { component, scanService, inventoryService } = create(admin, 'Etichetta smarrita');
        inventoryService.rotateQrCode.and.returnValue(of({ version: 3, issuedAt: '2026-10-27T10:00:00Z' }));
        component.ngOnInit();

        component['rotateCode']();

        expect(inventoryService.rotateQrCode).toHaveBeenCalledOnceWith(42, 'Etichetta smarrita');
        expect(component['rotatedVersion']).toBe(3);
        expect(component['result']?.itemId).toBe(42);
        expect(scanService.resolve).toHaveBeenCalledTimes(1);
    });

    it('does not rotate when the confirmation dialog is dismissed', () => {
        const { component, inventoryService } = create(admin, undefined);
        component.ngOnInit();

        component['rotateCode']();

        expect(inventoryService.rotateQrCode).not.toHaveBeenCalled();
    });

    it('reports an issue on an own assignment with its outstanding quantity as the limit', () => {
        const { component, dialogService, toastService } = create({ target: 'OWN_ASSIGNMENTS', allowedActions: [], assignments: [assignment(7, ['VIEW']), assignment(8, ['VIEW'])] }, { id: 1 });
        component.ngOnInit();

        component['reportAssignmentIssue'](component['result']!.assignments[1]);

        expect(dialogService.open.calls.mostRecent().args[1].data).toEqual({ assignmentId: 8, maxQuantity: 1 });
        expect(toastService.success).toHaveBeenCalled();
    });

    it('downloads the labels chosen in the print dialog', () => {
        const request = { layout: 'SINGLE_62X40', startCell: 0, showCutMarks: false, entries: [{ itemId: 42, copies: 1 }] };
        const { component, inventoryService } = create(admin, request);
        const labels = new Subject<Blob>();
        inventoryService.generateLabels.and.returnValue(labels);
        component.ngOnInit();

        component['printLabel']();

        expect(inventoryService.generateLabels).toHaveBeenCalledOnceWith(request as never);
    });
});
