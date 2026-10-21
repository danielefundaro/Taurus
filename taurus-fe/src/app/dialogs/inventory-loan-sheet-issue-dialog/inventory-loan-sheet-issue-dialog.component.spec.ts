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
        signature: { enabled: false, templateSignable: true, associationSigners: [] },
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
            associationSignatoryName: 'Anna Verdi',
            signatureMode: 'PAPER',
            associationSignerId: undefined,
            holderEmail: undefined
        });
    });

    it('proposes the electronic signature with the current administrator signing for the association', () => {
        const signature = { enabled: true, templateSignable: true, associationSigners: [{ userId: 'admin-1', displayName: 'Anna Verdi' }], defaultAssociationSignerId: 'admin-1' };
        const { component, close } = create(proposal({ signature }));
        component['selected'][4] = false;

        expect(component['electronic']).toBeTrue();
        component['save']();
        expect(close).toHaveBeenCalledWith(jasmine.objectContaining({ signatureMode: 'ELECTRONIC', associationSignerId: 'admin-1', associationSignatoryName: undefined }));

        component['associationSignerId'] = undefined;
        expect(component['invalidCount']).toBe(1);
    });

    it('asks the address of a parent or guardian who signs from the link', () => {
        const signature = { enabled: true, templateSignable: true, associationSigners: [{ userId: 'admin-1', displayName: 'Anna Verdi' }], defaultAssociationSignerId: 'admin-1' };
        const { component, close } = create(proposal({ signature }));
        component['selected'][4] = false;
        component['holderRole'] = 'GUARDIAN';
        component['onRoleChange']();

        expect(component['electronic']).toBeTrue();
        expect(component['guardianLink']).toBeTrue();
        expect(component['invalidCount']).toBe(1);
        expect(component['holderEmailError']).toContain('genitore o tutore');

        component['holderEmail'] = 'genitore@';
        expect(component['holderEmailError']).toContain('valido');
        component['save']();
        expect(close).not.toHaveBeenCalled();

        component['holderEmail'] = ' genitore@example.org ';
        expect(component['invalidCount']).toBe(0);
        component['save']();
        expect(close).toHaveBeenCalledWith(jasmine.objectContaining({ holderRole: 'GUARDIAN', signatureMode: 'ELECTRONIC', holderEmail: 'genitore@example.org' }));
    });

    it('keeps on paper the sheets of templates that cannot be signed', () => {
        const signature = { enabled: true, templateSignable: false, associationSigners: [], defaultAssociationSignerId: undefined };
        const { component } = create(proposal({ signature }));

        expect(component['electronic']).toBeFalse();
        expect(component['signatureMode']).toBe('PAPER');
        expect(component['electronicUnavailableReason']).toContain('non è firmabile');
        expect(component['request']().holderEmail).toBeUndefined();
    });

    it('requires the holder role when the birth date is unknown', () => {
        const { component } = create(proposal({ suggestedRole: undefined, roleRequired: true }));
        component['selected'][4] = false;

        expect(component['invalidCount']).toBe(1);
        component['holderRole'] = 'GUARDIAN';
        expect(component['invalidCount']).toBe(0);
    });
});
