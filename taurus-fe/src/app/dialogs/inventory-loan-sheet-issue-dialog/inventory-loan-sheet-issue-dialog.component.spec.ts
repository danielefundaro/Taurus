import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { InventoryLoanSheetProposal } from '../../module';
import { InventoryService } from '../../service';
import { InventoryLoanSheetIssueDialogComponent } from './inventory-loan-sheet-issue-dialog.component';

describe('InventoryLoanSheetIssueDialogComponent', () => {
    const proposal = (overrides: Partial<InventoryLoanSheetProposal> = {}): InventoryLoanSheetProposal => ({
        userIndex: 42,
        memberName: 'Mario',
        memberLastName: 'Rossi',
        suggestedRole: 'MEMBER',
        roleRequired: false,
        issuedOn: '2026-09-28',
        place: 'Roma',
        associationSignatoryName: 'Anna Verdi',
        terms: { templateId: 1, templateLabel: 'predefinito 2026-10-09', minMonths: 12, maxMonths: 24, renewalMaxMonths: 24, totalMaxMonths: 36 },
        assignments: [
            { assignmentId: 3, itemId: 30, itemName: 'Clarinetto', inventoryNumber: 'INV-3', loanType: 'FIXED_TERM', outstandingQuantity: 1, assignedOn: '2026-09-28', expirationDate: '2028-09-28', issues: [] },
            { assignmentId: 4, itemId: 40, itemName: 'Leggio', inventoryNumber: 'INV-4', outstandingQuantity: 1, assignedOn: '2026-09-28', issues: [{ assignmentId: 4, code: 'missingLoanType', args: {} }] }
        ],
        ...overrides
    });

    function create(value: InventoryLoanSheetProposal): { component: InventoryLoanSheetIssueDialogComponent; close: jasmine.Spy } {
        const close = jasmine.createSpy('close');
        const component = new InventoryLoanSheetIssueDialogComponent({ close } as unknown as DynamicDialogRef, {} as InventoryService, { data: { userIndex: 42, proposal: value } } as DynamicDialogConfig);
        return { component, close };
    }

    it('selects every good and blocks the issue while a selected good has problems', () => {
        const { component, close } = create(proposal());

        expect(component['selectedAssignments'].length).toBe(2);
        expect(component['invalidCount']).toBe(1);
        component['save']();
        expect(close).not.toHaveBeenCalled();

        component['selected'][4] = false;
        expect(component['invalidCount']).toBe(0);
        component['save']();
        expect(close).toHaveBeenCalledWith({
            assignmentIds: [3],
            issuedOn: '2026-09-28',
            place: 'Roma',
            holderRole: 'MEMBER',
            authorizationReference: undefined,
            authorizationDate: undefined,
            associationSignatoryName: 'Anna Verdi'
        });
    });

    it('requires the holder role when the birth date is unknown', () => {
        const { component } = create(proposal({ suggestedRole: undefined, roleRequired: true }));
        component['selected'][4] = false;

        expect(component['invalidCount']).toBe(1);
        component['holderRole'] = 'GUARDIAN';
        expect(component['invalidCount']).toBe(0);
    });
});
