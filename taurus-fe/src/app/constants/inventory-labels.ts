import { InventoryAssignmentStatus, InventoryDecisionType, InventoryReturnStatus } from '../module/inventory.module';

const assignmentStatusLabels: Record<InventoryAssignmentStatus, string> = {
    ACTIVE: 'Attiva',
    PARTIALLY_RETURNED: 'Parzialmente riconsegnata',
    RETURNED: 'Riconsegnata',
    CANCELLED: 'Annullata'
};

const decisionLabels: Record<InventoryDecisionType, string> = {
    ACCEPTED: 'Accettata',
    REJECTED: 'Rifiutata'
};

const returnStatusLabels: Record<InventoryReturnStatus, string> = {
    REQUESTED: 'Richiesta',
    COMPLETED: 'Completata',
    CANCELLED: 'Annullata'
};

export const inventoryAssignmentStatusLabel = (status: InventoryAssignmentStatus): string => assignmentStatusLabels[status];

export const inventoryDecisionLabel = (decision: InventoryDecisionType): string => decisionLabels[decision];

export const inventoryReturnStatusLabel = (status: InventoryReturnStatus): string => returnStatusLabels[status];
