export type FinancialAccountType = 'CASH' | 'BANK';
export type FinancialDirection = 'INCOME' | 'EXPENSE';
export type FinancialCategoryDirection = FinancialDirection | 'BOTH';
export type FinancialMovementNature = 'ORDINARY' | 'OPENING' | 'TRANSFER';
export type EventEconomicStatus = 'NO_BUDGET' | 'UNPLANNED_MOVEMENTS' | 'NO_MOVEMENTS' | 'OVERPAID_OR_OVERRUN' | 'SETTLED' | 'PARTIALLY_SETTLED';

export interface FinancialAccount {
    id?: number;
    name: string;
    description?: string;
    accountType: FinancialAccountType;
    currency: string;
    iban?: string;
    bankName?: string;
    active?: boolean;
    displayOrder?: number;
    initialBalance?: number;
    initialBalanceDate?: string;
    balance?: number;
    version?: number;
}

export interface FinancialCategory {
    id?: number;
    name: string;
    description?: string;
    direction: FinancialCategoryDirection;
    active?: boolean;
    systemDefined?: boolean;
    displayOrder?: number;
    version?: number;
}

export interface FinancialMovement {
    id?: number;
    accountingYear?: number;
    accountId: number;
    accountName?: string;
    categoryId?: number;
    categoryName?: string;
    eventId?: number;
    eventName?: string;
    direction: FinancialDirection;
    nature?: FinancialMovementNature;
    bookingDate: string;
    valueDate?: string;
    amount: number;
    currency?: string;
    description: string;
    counterparty?: string;
    documentReference?: string;
    notes?: string;
    transferGroup?: string;
    reconciled?: boolean;
    reconciledAt?: string;
    reconciliationReference?: string;
    version?: number;
}

export interface FinancialTransferRequest {
    sourceAccountId: number;
    destinationAccountId: number;
    bookingDate: string;
    valueDate?: string;
    amount: number;
    description: string;
    notes?: string;
}

export interface FinancialDashboard {
    totalBalance: number;
    income: number;
    expense: number;
    result: number;
    movementCount: number;
    unreconciledCount: number;
    accounts: FinancialAccount[];
}

export interface FinancialAttachment {
    id: number;
    movementId: number;
    mediaAssetId: number;
    fileName: string;
    mimeType: string;
    fileSize: number;
    description?: string;
}

export interface FinancialEventSummary {
    eventId: number;
    eventName: string;
    expectedFee: number;
    expectedCosts: number;
    expectedCostItems: Array<{ id?: number; description: string; amount: number }>;
    expectedMargin: number;
    received: number;
    paid: number;
    actualResult: number;
    remainingIncome: number;
    remainingExpense: number;
    economicStatus: EventEconomicStatus;
    movements: FinancialMovement[];
}

export interface AccountingYear {
    year: number;
    startDate: string;
    endDate: string;
    status: 'OPEN' | 'ROLLED_OVER';
    rolledOverAt?: string;
    lastRecalculatedAt?: string;
}
