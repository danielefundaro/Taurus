import { InventoryAssignmentStatus, InventoryCondition, InventoryDecisionType, InventoryLoanHolderRole, InventoryLoanReturnOutcome, InventoryLoanSheetStatus, InventoryLoanType, InventoryReturnStatus } from '../module/inventory.module';

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

const loanTypeLabels: Record<InventoryLoanType, string> = {
    FIXED_TERM: 'Comodato con scadenza',
    PRECARIOUS: 'Comodato precario'
};

const loanSheetStatusLabels: Record<InventoryLoanSheetStatus, string> = {
    ISSUED: 'Emesso',
    CLOSED: 'Chiuso',
    SETTLED: 'Definito',
    CANCELLED: 'Annullato'
};

const loanHolderRoleLabels: Record<InventoryLoanHolderRole, string> = {
    MEMBER: 'Socio maggiorenne',
    GUARDIAN: 'Genitore o tutore'
};

const loanReturnOutcomeLabels: Record<InventoryLoanReturnOutcome, string> = {
    COMPLIANT: 'Nessun addebito',
    CHARGES: 'Con addebito'
};

/** Opzioni del regime di comodato con la qualificazione giuridica mostrata nei moduli. */
export const inventoryLoanTypeOptions: { label: string; value: InventoryLoanType; description: string }[] = [
    { label: 'Strumento musicale o accessorio', value: 'FIXED_TERM', description: 'Comodato d’uso con scadenza (art. 1809 c.c.)' },
    { label: 'Divisa, accessorio o altro bene', value: 'PRECARIOUS', description: 'Comodato d’uso precario (art. 1810 c.c.)' }
];

export const inventoryLoanTypeLabel = (value?: InventoryLoanType | null): string => (value ? loanTypeLabels[value] : 'Regime non indicato');

export const inventoryLoanSheetStatusLabel = (status: InventoryLoanSheetStatus): string => loanSheetStatusLabels[status];

export const inventoryLoanSheetStatusSeverity = (status: InventoryLoanSheetStatus): 'info' | 'success' | 'secondary' | 'warn' => (status === 'ISSUED' ? 'info' : status === 'CLOSED' ? 'warn' : status === 'SETTLED' ? 'success' : 'secondary');

export const inventoryLoanHolderRoleLabel = (role: InventoryLoanHolderRole): string => loanHolderRoleLabels[role];

export const inventoryLoanReturnOutcomeLabel = (outcome: InventoryLoanReturnOutcome): string => loanReturnOutcomeLabels[outcome];
