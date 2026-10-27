import { signal } from '@angular/core';
import { fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { DialogService } from 'primeng/dynamicdialog';
import { of } from 'rxjs';
import { InventoryIssueListComponent } from '../../../components/inventory-issue-list/inventory-issue-list.component';
import { InventoryAssignment } from '../../../module';
import { SignatureService, TenantFeatureService, ToastService, UserInventoryService } from '../../../service';
import { InventoryAssignmentDetailComponent } from './assignment-detail.component';

describe('InventoryAssignmentDetailComponent', () => {
    const assignment = { id: 7, itemName: 'Leggio', inventoryNumber: 'LEG-1', outstandingQuantity: 2, returns: [], photos: [] } as unknown as InventoryAssignment;

    function create(fragment: string | null, dialogResult?: unknown) {
        const inventoryService = jasmine.createSpyObj<UserInventoryService>('UserInventoryService', ['getAssignment']);
        inventoryService.getAssignment.and.returnValue(of(assignment));
        const dialogService = jasmine.createSpyObj<DialogService>('DialogService', ['open']);
        dialogService.open.and.returnValue({ onClose: of(dialogResult) } as never);
        const toastService = jasmine.createSpyObj<ToastService>('ToastService', ['success', 'error']);
        const component = new InventoryAssignmentDetailComponent(
            inventoryService,
            toastService,
            dialogService,
            {} as SignatureService,
            { inventoryQrEnabled: signal(true), inventorySignatureEnabled: signal(false) } as unknown as TenantFeatureService,
            { params: of({ id: '7' }), snapshot: { fragment } } as unknown as ActivatedRoute,
            jasmine.createSpyObj<Router>('Router', ['navigate'])
        );
        return { component, inventoryService, dialogService, toastService };
    }

    it('opens the section named by the scan once the assignment is loaded', fakeAsync(() => {
        const section = document.createElement('h2');
        section.id = 'riconsegna';
        document.body.appendChild(section);
        try {
            const { component, inventoryService } = create('riconsegna');

            component.ngOnInit();
            tick();

            expect(inventoryService.getAssignment).toHaveBeenCalledOnceWith(7);
            expect(document.activeElement).toBe(section);
        } finally {
            section.remove();
        }
    }));

    it('refreshes the issue section after reporting from the page header', () => {
        const { component, dialogService, toastService } = create(null, { id: 900 });
        const issueList = jasmine.createSpyObj<InventoryIssueListComponent>('InventoryIssueListComponent', ['reload']);
        component.ngOnInit();
        component['issueList'] = issueList;

        component['reportIssue']();

        expect(dialogService.open.calls.mostRecent().args[1].data).toEqual({ assignmentId: 7, maxQuantity: 2 });
        expect(toastService.success).toHaveBeenCalled();
        expect(issueList.reload).toHaveBeenCalled();
    });
});
