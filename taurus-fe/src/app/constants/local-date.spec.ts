import { inventoryLoanHolderRoleLabel, inventoryLoanReturnOutcomeLabel, inventoryLoanSheetStatusLabel, inventoryLoanSheetStatusSeverity, inventoryLoanTypeLabel } from './inventory-labels';
import { addMonths, fromLocalDate, toLocalDate } from './local-date';

describe('local dates', () => {
    it('round-trips ISO local dates without time zone shifts', () => {
        const date = fromLocalDate('2026-09-28')!;
        expect(date.getFullYear()).toBe(2026);
        expect(date.getMonth()).toBe(8);
        expect(date.getDate()).toBe(28);
        expect(toLocalDate(date)).toBe('2026-09-28');
        expect(toLocalDate(null)).toBeUndefined();
        expect(fromLocalDate(undefined)).toBeUndefined();
    });

    it('adds months clamping to the end of the month like the backend', () => {
        expect(toLocalDate(addMonths(new Date(2027, 0, 31), 1))).toBe('2027-02-28');
        expect(toLocalDate(addMonths(new Date(2026, 8, 28), 24))).toBe('2028-09-28');
    });
});

describe('loan sheet labels', () => {
    it('translates the loan regime, the sheet state, the holder and the outcome', () => {
        expect(inventoryLoanTypeLabel('FIXED_TERM')).toBe('Comodato con scadenza');
        expect(inventoryLoanTypeLabel('PRECARIOUS')).toBe('Comodato precario');
        expect(inventoryLoanTypeLabel(null)).toBe('Regime non indicato');
        expect(inventoryLoanSheetStatusLabel('SETTLED')).toBe('Definito');
        expect(inventoryLoanSheetStatusSeverity('ISSUED')).toBe('info');
        expect(inventoryLoanSheetStatusSeverity('CANCELLED')).toBe('secondary');
        expect(inventoryLoanHolderRoleLabel('GUARDIAN')).toBe('Genitore o tutore');
        expect(inventoryLoanReturnOutcomeLabel('CHARGES')).toBe('Con addebito');
    });
});
