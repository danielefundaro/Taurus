import { InventoryAssignmentStatus, InventoryCondition, InventoryDecisionType, InventoryReturnStatus } from '../module/inventory.module';

const conditionLabels: Record<InventoryCondition, string> = {
    NEW: 'Nuovo',
    EXCELLENT: 'Eccellente',
    GOOD: 'Buono',
    FAIR: 'Discreto',
    TO_REPAIR: 'Da riparare',
    OUT_OF_SERVICE: 'Fuori servizio'
};

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

export const inventoryConditionLabel = (condition: InventoryCondition | string): string => conditionLabels[condition as InventoryCondition] ?? 'Stato non disponibile';

export const inventoryDecisionLabel = (decision: InventoryDecisionType): string => decisionLabels[decision];

export const inventoryReturnStatusLabel = (status: InventoryReturnStatus): string => returnStatusLabels[status];
