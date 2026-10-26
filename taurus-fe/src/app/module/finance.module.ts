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
    /** Spiega da dove viene il saldo iniziale: finisce sulla descrizione del movimento di apertura. */
    initialBalanceDescription?: string;
    /** Movimento di apertura del saldo iniziale, a cui allegare il documento che lo giustifica. */
    initialBalanceMovementId?: number;
    balance?: number;
    /** Scritture collegate ancora vive, escluse quelle eliminate: a zero il conto è eliminabile. */
    movementCount?: number;
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
    /** Documenti allegati ancora validi: a zero il movimento è senza giustificativo. */
    attachmentCount?: number;
    /** Chiave di idempotenza generata dal client: un retry non crea un secondo movimento. */
    requestKey?: string;
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
    /** Chiave di idempotenza generata dal client: un retry non sposta il denaro due volte. */
    requestKey?: string;
}

export interface FinancialDashboard {
    from: string;
    to: string;
    totalBalance: number;
    income: number;
    expense: number;
    result: number;
    movementCount: number;
    unreconciledCount: number;
    /** Posizioni evento ancora aperte: quanto resta da incassare e quanto da pagare. */
    eventsToCollect: number;
    eventsToPay: number;
    openEvents: FinancialEventLine[];
    /** Ultimo riporto generato, per sapere se le aperture sono allineate. */
    lastRollover?: AccountingYear;
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

export interface FinancialStatementLine {
    movement: FinancialMovement;
    balance: number;
}

export interface FinancialAccountStatement {
    account: FinancialAccount;
    from: string;
    to: string;
    openingBalance: number;
    income: number;
    expense: number;
    closingBalance: number;
    lines: FinancialStatementLine[];
}

export interface FinancialCategoryTotal {
    categoryId?: number;
    categoryName: string;
    direction?: FinancialCategoryDirection;
    income: number;
    expense: number;
    net: number;
    movementCount: number;
}

export interface FinancialAccountYearBalance {
    accountId: number;
    accountName: string;
    openingBalance: number;
    income: number;
    expense: number;
    closingBalance: number;
}

export interface FinancialEventLine {
    eventId: number;
    eventName: string;
    eventDate?: string;
    expectedFee: number;
    expectedCosts: number;
    expectedMargin: number;
    received: number;
    paid: number;
    actualResult: number;
    remainingIncome: number;
    remainingExpense: number;
    economicStatus: EventEconomicStatus;
}

/** Segnalazione informativa su un esercizio: non impedisce nulla, indica cosa controllare. */
export interface FinancialYearAnomaly {
    code: string;
    message: string;
    movementId?: number;
    transferGroup?: string;
}

/** Preventivo, consuntivo e scostamento di un evento. */
export interface FinancialEventStatement {
    summary: FinancialEventSummary;
    feeVariance: number;
    costVariance: number;
    resultVariance: number;
}

export interface AccountingYearSummary {
    year: AccountingYear;
    accounts: FinancialAccountYearBalance[];
    openingTotal: number;
    ordinaryIncome: number;
    ordinaryExpense: number;
    ordinaryResult: number;
    transferTotal: number;
    closingTotal: number;
    categories: FinancialCategoryTotal[];
    openEvents: FinancialEventLine[];
    unreconciledCount: number;
    unreconciledAmount: number;
    anomalies: FinancialYearAnomaly[];
    lastRecalculatedAt?: string;
}
