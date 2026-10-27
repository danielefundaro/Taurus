import { DialogService } from 'primeng/dynamicdialog';
import { of } from 'rxjs';
import { InventoryIssue } from '../../module';
import { ConfirmRequest, ConfirmService, InventoryService, ToastService, UserInventoryService } from '../../service';
import { InventoryIssueListComponent } from './inventory-issue-list.component';

describe('InventoryIssueListComponent', () => {
    const issue: InventoryIssue = {
        id: 900,
        itemId: 42,
        assignmentId: 7,
        inventoryNumber: 'LEG-0012',
        itemName: 'Leggio',
        reportedQuantity: 1,
        severity: 'UNSAFE',
        description: 'Asta piegata',
        status: 'OPEN',
        reportedAt: '2026-10-27T10:00:00Z',
        version: 2,
        photos: [{ id: 5, fileName: 'asta.jpg', contentType: 'image/jpeg', fileSize: 1, displayOrder: 0 }]
    };

    function create(dialogResult?: unknown) {
        const inventoryService = jasmine.createSpyObj<InventoryService>('InventoryService', ['getIssues', 'transitionIssue', 'uploadIssuePhoto', 'eraseIssuePhoto', 'issuePhotoUrl']);
        const userInventoryService = jasmine.createSpyObj<UserInventoryService>('UserInventoryService', ['getAssignmentIssues', 'uploadIssuePhoto', 'issuePhotoUrl']);
        inventoryService.getIssues.and.returnValue(of([issue]));
        userInventoryService.getAssignmentIssues.and.returnValue(of([issue]));
        inventoryService.issuePhotoUrl.and.callFake((id) => `/api/inventory/issue-photos/${id}`);
        userInventoryService.issuePhotoUrl.and.callFake((id) => `/api/user/inventory/issue-photos/${id}`);
        const dialogService = jasmine.createSpyObj<DialogService>('DialogService', ['open']);
        dialogService.open.and.returnValue({ onClose: of(dialogResult) } as never);
        const confirmService = jasmine.createSpyObj<ConfirmService>('ConfirmService', ['confirmDestructive']);
        const toastService = jasmine.createSpyObj<ToastService>('ToastService', ['success', 'error']);
        const component = new InventoryIssueListComponent(inventoryService, userInventoryService, dialogService, confirmService, toastService);
        return { component, inventoryService, userInventoryService, dialogService, confirmService, toastService };
    }

    it('loads the issues of the item for administrators', () => {
        const { component, inventoryService, userInventoryService } = create();
        component.itemId = 42;

        component.ngOnChanges();

        expect(inventoryService.getIssues).toHaveBeenCalledOnceWith(42);
        expect(userInventoryService.getAssignmentIssues).not.toHaveBeenCalled();
        expect(component['photoUrl'](5)).toBe('/api/inventory/issue-photos/5');
    });

    it('uses the personal endpoints for an own assignment', () => {
        const { component, inventoryService, userInventoryService, dialogService } = create({ id: 901 });
        component.assignmentId = 7;
        component.totalQuantity = 2;

        component.ngOnChanges();
        component['create']();

        expect(userInventoryService.getAssignmentIssues).toHaveBeenCalledTimes(2);
        expect(inventoryService.getIssues).not.toHaveBeenCalled();
        expect(dialogService.open.calls.mostRecent().args[1].data).toEqual({ assignmentId: 7, maxQuantity: 2 });
        expect(component['photoUrl'](5)).toBe('/api/user/inventory/issue-photos/5');
    });

    it('uploads a photo of an own issue through the personal endpoint', () => {
        const { component, userInventoryService, inventoryService } = create();
        userInventoryService.uploadIssuePhoto.and.returnValue(of({}));
        component.assignmentId = 7;
        const file = new File(['x'], 'asta.jpg', { type: 'image/jpeg' });
        const input = document.createElement('input');
        Object.defineProperty(input, 'files', { value: [file] });

        component['upload'](issue, { target: input } as unknown as Event);

        expect(userInventoryService.uploadIssuePhoto).toHaveBeenCalledOnceWith(900, file);
        expect(inventoryService.uploadIssuePhoto).not.toHaveBeenCalled();
    });

    it('erases a photo only after the destructive confirmation', () => {
        const { component, inventoryService, confirmService, toastService } = create();
        inventoryService.eraseIssuePhoto.and.returnValue(of(undefined));
        component.itemId = 42;

        component['erasePhoto'](issue, issue.photos[0]);

        expect(inventoryService.eraseIssuePhoto).not.toHaveBeenCalled();
        const request = confirmService.confirmDestructive.calls.mostRecent().args[0] as ConfirmRequest;
        expect(request.consequence).toContain('asta.jpg');
        request.accept();
        expect(inventoryService.eraseIssuePhoto).toHaveBeenCalledOnceWith(5);
        expect(toastService.success).toHaveBeenCalled();
        expect(inventoryService.getIssues).toHaveBeenCalledOnceWith(42);
    });

    it('sends the transition with the version, the notes and the new condition', () => {
        const { component, inventoryService } = create();
        inventoryService.transitionIssue.and.returnValue(of(issue));
        component.itemId = 42;
        component['resolutionNotes'][900] = 'Asta sostituita';
        component['resolutionConditions'][900] = 'GOOD';

        expect(component['hasResolutionNotes'](issue)).toBeTrue();
        component['transition'](issue, 'RESOLVED');

        expect(inventoryService.transitionIssue).toHaveBeenCalledOnceWith(900, 'RESOLVED', 2, 'Asta sostituita', 'GOOD');
    });

    it('labels severity and status in words', () => {
        const { component } = create();

        expect(component['severityLabel'](issue)).toBe('Non sicuro');
        expect(component['statusLabel']('ACKNOWLEDGED')).toBe('Presa in carico');
        expect(component['isTerminal']({ ...issue, status: 'DISMISSED' })).toBeTrue();
    });
});
