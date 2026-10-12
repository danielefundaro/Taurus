import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { toLocalDate } from '../../constants';
import { InventoryAssignment } from '../../module';
import { InventoryRenewalDialogComponent } from './inventory-renewal-dialog.component';

describe('InventoryRenewalDialogComponent', () => {
    function create(expirationDate: string): { component: InventoryRenewalDialogComponent; close: jasmine.Spy } {
        const close = jasmine.createSpy('close');
        const assignment = { id: 9, itemName: 'Clarinetto', inventoryNumber: 'INV-3', expirationDate, loanSheetNumber: '2026/001' } as InventoryAssignment;
        const component = new InventoryRenewalDialogComponent(
            { close } as unknown as DynamicDialogRef,
            { data: { assignment, terms: { templateId: 1, templateLabel: 'predefinito 2026-10-09', minMonths: 12, maxMonths: 24, renewalMaxMonths: 12, totalMaxMonths: 36 } } } as DynamicDialogConfig
        );
        return { component, close };
    }

    it('proposes the longest renewal and requires the authorization before the renewal date', () => {
        const { component, close } = create('2027-09-28');
        component['renewedOn'] = new Date(2027, 8, 1);

        expect(toLocalDate(component['newExpirationDate'])).toBe('2028-09-28');
        expect(toLocalDate(component['minimumExpiration'])).toBe('2027-09-29');
        expect(component['invalidCount']).toBe(2);

        component['authorizationReference'] = '12';
        component['authorizationDate'] = new Date(2027, 8, 2);
        expect(component['invalidCount']).toBe(1);

        component['authorizationDate'] = new Date(2027, 7, 30);
        component['save']();
        expect(close).toHaveBeenCalledWith({
            newExpirationDate: '2028-09-28',
            authorizationReference: '12',
            authorizationDate: '2027-08-30',
            renewedOn: '2027-09-01'
        });
    });

    it('does not renew after the current expiration', () => {
        const { component } = create('2027-09-28');
        component['authorizationReference'] = '12';
        component['authorizationDate'] = new Date(2027, 8, 1);
        component['renewedOn'] = new Date(2027, 8, 29);

        expect(component['expired']).toBeTrue();
        expect(component['invalidCount']).toBe(1);
    });
});
