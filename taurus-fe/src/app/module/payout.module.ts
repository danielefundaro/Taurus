/** Compensi ai presenti (docs/event-payout-shares-spec.md): i valori arrivano già calcolati dal backend. */

export type PayoutLineKind = 'SHARE' | 'FIXED';

export type PayoutMemberStatus = 'ALL' | 'DUE' | 'SETTLED';

export interface PayoutSetting {
    id: number;
    validFrom: string;
    associationSharePercent: number;
    roundingStep: number;
    createdBy?: string;
    createdAt?: string;
}

export interface PayoutSettingRequest {
    validFrom: string;
    associationSharePercent: number;
    roundingStep: number;
}

/** Un evento già liquidato che una versione con quella decorrenza ricalcolerebbe. */
export interface PayoutSettledEvent {
    eventId: number;
    eventName: string;
    eventDate: string;
    settledAmount: number;
}

/** Dove vanno i soldi dell'evento, sul preventivo o sul consuntivo. */
export interface PayoutDistribution {
    base: number;
    expenses: number;
    fixedPayments: number;
    margin: number;
    fullAssociationShare: number;
    associationShare: number;
    associationShareReduced: boolean;
    net: number;
    baseShare: number;
    sharesTotal: number;
    residual: number;
    deficit: number;
    effectiveAssociationShare: number;
    sharingParticipants: number;
}

export interface PayoutEventLine {
    userId: number;
    name: string;
    present: boolean;
    kind: PayoutLineKind;
    factor?: number | null;
    fixedAmount?: number | null;
    reason?: string | null;
    simulatedTheoreticalShare?: number | null;
    simulatedShare: number;
    realTheoreticalShare?: number | null;
    realShare: number;
    settled: number;
    due: number;
    deviation: number;
}

export interface PayoutEvent {
    eventId: number;
    eventName: string;
    eventDate: string;
    settingId: number;
    settingValidFrom: string;
    savedSharePercent: number;
    savedRoundingStep: number;
    sharePercent: number;
    roundingStep: number;
    preview: boolean;
    incomeRecorded: boolean;
    planned: PayoutDistribution;
    actual: PayoutDistribution;
    lines: PayoutEventLine[];
    settledTotal: number;
    dueTotal: number;
    deviationTotal: number;
}

/** Fattore o pagamento fisso: esattamente uno dei due, sempre con il motivo. */
export interface PayoutAdjustmentRequest {
    factor?: number | null;
    fixedAmount?: number | null;
    reason: string;
}

export interface PayoutMemberSummary {
    userId: number;
    name: string;
    eventCount: number;
    dueTotal: number;
    settledTotal: number;
    deviationTotal: number;
}

export interface PayoutMemberLine {
    eventId: number;
    eventName: string;
    eventDate: string;
    present: boolean;
    configured: boolean;
    incomeRecorded: boolean;
    kind: PayoutLineKind;
    factor?: number | null;
    fixedAmount?: number | null;
    reason?: string | null;
    simulatedShare: number;
    realShare: number;
    settled: number;
    due: number;
    deviation: number;
}

export interface PayoutMember {
    userId: number;
    name: string;
    dueTotal: number;
    settledTotal: number;
    deviationTotal: number;
    lines: PayoutMemberLine[];
}

export interface PayoutSettlementLineRequest {
    eventId: number;
    userId: number;
    amount: number;
}

export interface PayoutSettlementRequest {
    accountId: number;
    categoryId?: number | null;
    bookingDate: string;
    description?: string | null;
    notes?: string | null;
    requestKey?: string;
    lines: PayoutSettlementLineRequest[];
}

export interface PayoutSettlement {
    movementId: number;
    userId: number;
    userName: string;
    bookingDate: string;
    total: number;
    eventCount: number;
    lines: Array<{ id: number; eventId: number; eventName: string; userId: number; amount: number }>;
}
