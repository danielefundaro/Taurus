import { inventoryLoanHolderRoleLabel, inventoryLoanReturnOutcomeLabel, inventoryLoanSheetStatusLabel, inventoryLoanSheetStatusSeverity, inventoryLoanTypeLabel } from './inventory-labels';
import { addMonths, fromApiDate, fromLocalDate, instantOfWallClock, nextMorningIn, toLocalDate } from './local-date';

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

    /** Legge l'istante con l'orologio di una zona, così l'asserzione non dipende dal fuso del browser. */
    const wallClock = (zone: string, moment: Date): string =>
        new Intl.DateTimeFormat('en-GB', {
            timeZone: zone,
            year: 'numeric',
            month: '2-digit',
            day: '2-digit',
            hour: '2-digit',
            minute: '2-digit',
            hour12: false
        }).format(moment);

    it('places tomorrow morning on the clock of the profile time zone', () => {
        const reference = new Date(Date.UTC(2026, 9, 26, 21, 30));

        expect(wallClock('Europe/Rome', nextMorningIn('Europe/Rome', 8, reference))).toBe('27/10/2026, 08:00');
        // A Tokyo sono già le 6:30 del 27: la mattina successiva è quella del 28, non del 27.
        expect(wallClock('Asia/Tokyo', nextMorningIn('Asia/Tokyo', 8, reference))).toBe('28/10/2026, 08:00');
        expect(wallClock('Pacific/Honolulu', nextMorningIn('Pacific/Honolulu', 8, reference))).toBe('27/10/2026, 08:00');
    });

    it('keeps the chosen hour across a daylight saving change', () => {
        // L'ora legale italiana finisce la notte del 25 ottobre 2026: l'offset del giorno dopo
        // non è quello del riferimento, e le 8:00 devono restare le 8:00.
        const beforeChange = new Date(Date.UTC(2026, 9, 24, 12, 0));
        const morning = nextMorningIn('Europe/Rome', 8, beforeChange);

        expect(wallClock('Europe/Rome', morning)).toBe('25/10/2026, 08:00');
        expect(morning.toISOString()).toBe('2026-10-25T07:00:00.000Z');
    });

    it('reads a wall clock time as the instant of that zone', () => {
        const wall = new Date(2026, 6, 15, 18, 45);

        expect(instantOfWallClock('Europe/Rome', wall).toISOString()).toBe('2026-07-15T16:45:00.000Z');
        expect(instantOfWallClock('UTC', wall).toISOString()).toBe('2026-07-15T18:45:00.000Z');
    });

    it('reads the date-only values of the API as local dates, whatever their format', () => {
        expect(toLocalDate(fromApiDate('1990-03-04'))).toBe('1990-03-04');
        expect(toLocalDate(fromApiDate(new Date(1990, 2, 4, 15, 30)))).toBe('1990-03-04');
        expect(toLocalDate(fromApiDate(new Date(1990, 2, 4).getTime()))).toBe('1990-03-04');
        expect(fromApiDate(null)).toBeUndefined();
        expect(fromApiDate('')).toBeUndefined();
        expect(fromApiDate('non una data')).toBeUndefined();
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
