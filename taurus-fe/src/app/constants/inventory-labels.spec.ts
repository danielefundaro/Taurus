import { inventoryAssignmentStatusLabel, inventoryDecisionLabel, inventoryReturnStatusLabel } from './inventory-labels';

describe('Inventory labels', () => {
    it('translates assignment statuses', () => {
        expect(inventoryAssignmentStatusLabel('ACTIVE')).toBe('Attiva');
        expect(inventoryAssignmentStatusLabel('PARTIALLY_RETURNED')).toBe('Parzialmente riconsegnata');
        expect(inventoryAssignmentStatusLabel('RETURNED')).toBe('Riconsegnata');
        expect(inventoryAssignmentStatusLabel('CANCELLED')).toBe('Annullata');
    });

    it('translates decisions', () => {
        expect(inventoryDecisionLabel('ACCEPTED')).toBe('Accettata');
        expect(inventoryDecisionLabel('REJECTED')).toBe('Rifiutata');
    });

    it('translates return statuses', () => {
        expect(inventoryReturnStatusLabel('REQUESTED')).toBe('Richiesta');
        expect(inventoryReturnStatusLabel('COMPLETED')).toBe('Completata');
        expect(inventoryReturnStatusLabel('CANCELLED')).toBe('Annullata');
    });
});
