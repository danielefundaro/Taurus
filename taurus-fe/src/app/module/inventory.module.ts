import type { SignatureIssueOptions, SignatureSettings } from './signature.module';
import { ApiErrorIssue } from './api-error.module';

export type InventoryCondition = 'NEW' | 'EXCELLENT' | 'GOOD' | 'FAIR' | 'TO_REPAIR' | 'OUT_OF_SERVICE';
export type InventoryDecisionType = 'ACCEPTED' | 'REJECTED';
export type InventoryAssignmentStatus = 'ACTIVE' | 'PARTIALLY_RETURNED' | 'RETURNED' | 'CANCELLED';
export type InventoryReturnStatus = 'REQUESTED' | 'COMPLETED' | 'CANCELLED';
export type InventoryAssignmentScope = 'POSSESSED' | 'RETURNED';
export type InventoryLoanType = 'FIXED_TERM' | 'PRECARIOUS';
export type InventoryLoanSheetStatus = 'ISSUED' | 'CLOSED' | 'SETTLED' | 'CANCELLED';
export type InventoryLoanHolderRole = 'MEMBER' | 'GUARDIAN';
/** How the acts of a sheet are signed: on the printed sheet or electronically in Taurus. */
export type InventoryLoanSignatureMode = 'PAPER' | 'ELECTRONIC';
export type InventoryLoanReturnOutcome = 'COMPLIANT' | 'CHARGES';
export type InventoryLoanTemplateOrigin = 'DEFAULT' | 'CUSTOM';
export type InventoryLoanTemplateVariableType = 'TEXT' | 'FLAG' | 'LIST';

export interface InventoryPhoto {
    id: number;
    fileName: string;
    contentType: string;
    fileSize: number;
    displayOrder: number;
    preview: boolean;
}

export interface InventoryDecision {
    decision: InventoryDecisionType;
    rejectionReason?: string;
    decidedAt: string;
}

export interface InventoryReturn {
    id: number;
    quantity: number;
    status: InventoryReturnStatus;
    requestedAt: string;
    completedAt?: string;
    condition?: InventoryCondition;
    notes?: string;
    photos: InventoryPhoto[];
}

export interface InventoryAssignment {
    id: number;
    itemId: number;
    inventoryNumber: string;
    itemName: string;
    itemDescription?: string;
    estimatedUnitValue?: number;
    currency?: string;
    conditionStatus: InventoryCondition;
    conditionNotes?: string;
    expirationDate?: string;
    userIndex: number;
    userName: string;
    userLastName: string;
    order: number;
    assignedQuantity: number;
    returnedQuantity: number;
    outstandingQuantity: number;
    assignedAt: string;
    description?: string;
    status: InventoryAssignmentStatus;
    revision: number;
    revisionHash: string;
    revisionDate: string;
    decision?: InventoryDecision;
    returns: InventoryReturn[];
    photos: InventoryPhoto[];
    brandModel?: string;
    serialNumber?: string;
    loanType?: InventoryLoanType;
    accessories?: string;
    loanSheetId?: number;
    loanSheetNumber?: string;
    replacesAssignmentId?: number;
}

export interface InventoryAssignmentSummary {
    id: number;
    itemId: number;
    inventoryNumber: string;
    itemName: string;
    itemDescription?: string;
    estimatedUnitValue?: number;
    currency?: string;
    conditionStatus: InventoryCondition;
    expirationDate?: string;
    assignedQuantity: number;
    returnedQuantity: number;
    outstandingQuantity: number;
    assignedAt: string;
    status: InventoryAssignmentStatus;
    revision: number;
    revisionHash: string;
    revisionDate: string;
    decision?: InventoryDecision;
    photo?: InventoryPhoto;
}

export interface InventoryItem {
    id?: number;
    inventoryNumber: string;
    name: string;
    description?: string;
    totalQuantity: number;
    assignedQuantity?: number;
    availableQuantity?: number;
    estimatedUnitValue?: number;
    currency?: string;
    conditionStatus: InventoryCondition;
    conditionNotes?: string;
    version?: number;
    photos?: InventoryPhoto[];
    assignments?: InventoryAssignment[];
    brandModel?: string;
    serialNumber?: string;
    loanType?: InventoryLoanType | null;
}

export interface InventoryAssignmentRequest {
    userIndex: number;
    order: number;
    quantity: number;
    description?: string;
    expirationDate?: string;
    accessories?: string;
}

/**
 * Scadenze ammesse per un comodato a scadenza consegnato il giorno `deliveredOn`: regole del foglio emesso indicato
 * da `sheetNumber`, oppure regole correnti del tenant.
 */
export interface InventoryLoanExpirationRange {
    deliveredOn: string;
    minimum: string;
    maximum: string;
    sheetNumber?: string | null;
}

/** Modello e regole di durata di un foglio, oppure del tenant per un foglio ancora da emettere. */
export interface InventoryLoanSheetTerms {
    templateId?: number;
    templateLabel: string;
    minMonths: number;
    maxMonths: number;
    renewalMaxMonths: number;
    totalMaxMonths: number;
}

export interface InventoryLoanSheetSummary {
    id: number;
    sheetNumber: string;
    status: InventoryLoanSheetStatus;
    issuedOn: string;
    userIndex: number;
    memberName: string;
    memberLastName: string;
    holderRole: InventoryLoanHolderRole;
    templateLabel: string;
    returnOutcome?: InventoryLoanReturnOutcome;
    closedAt?: string;
    settledAt?: string;
    cancelledAt?: string;
    personalDataPurged: boolean;
    closable: boolean;
    entityVersion: number;
    signatureMode: InventoryLoanSignatureMode;
}

export interface InventoryLoanSheetLine {
    lineNumber: number;
    assignmentId: number;
    itemName: string;
    brandModel?: string;
    serialNumber?: string;
    inventoryNumber: string;
    accessories?: string;
    quantity: number;
    agreedUnitValue?: number;
    conditionStatus?: InventoryCondition;
    conditionNotes?: string;
    loanType: InventoryLoanType;
    expirationDate?: string;
}

export interface InventoryLoanSheetRegisterEntry {
    date?: string;
    operation: 'C' | 'R' | 'S' | 'N';
    itemName: string;
    brandModelSerial?: string;
    expirationDate?: string;
    agreedValue?: string;
    authorization?: string;
}

export interface InventoryLoanSheetLinkedAssignment {
    assignmentId: number;
    itemId: number;
    itemName: string;
    inventoryNumber: string;
    loanType?: InventoryLoanType;
    assignedQuantity: number;
    outstandingQuantity: number;
    status: InventoryAssignmentStatus;
    expirationDate?: string;
    variation: boolean;
    replacesAssignmentId?: number;
}

export interface InventoryLoanSheetDetail {
    summary: InventoryLoanSheetSummary;
    place?: string;
    authorizationReference?: string;
    authorizationDate?: string;
    associationSignatoryName?: string;
    chargeAmount?: number;
    chargeDueDate?: string;
    receivedByName?: string;
    cancellationReason?: string;
    terms: InventoryLoanSheetTerms;
    lines: InventoryLoanSheetLine[];
    register: InventoryLoanSheetRegisterEntry[];
    assignments: InventoryLoanSheetLinkedAssignment[];
}

/** A duration problem of a good in the sheet proposal: `code` is an `inventory.loanDuration.*` code. */
export interface InventoryLoanSheetIssue extends ApiErrorIssue {
    assignmentId: number;
}

export interface InventoryLoanSheetProposalAssignment {
    assignmentId: number;
    itemId: number;
    itemName: string;
    inventoryNumber: string;
    loanType?: InventoryLoanType;
    outstandingQuantity: number;
    assignedOn: string;
    expirationDate?: string;
    issues: InventoryLoanSheetIssue[];
}

export interface InventoryLoanSheetProposal {
    userIndex: number;
    memberName: string;
    memberLastName: string;
    memberBirthDate?: string;
    suggestedRole?: InventoryLoanHolderRole;
    roleRequired: boolean;
    issuedOn: string;
    place?: string;
    associationSignatoryName?: string;
    openSheetId?: number;
    openSheetNumber?: string;
    terms: InventoryLoanSheetTerms;
    assignments: InventoryLoanSheetProposalAssignment[];
    signature: SignatureIssueOptions;
}

export interface InventoryLoanSheetIssueRequest {
    assignmentIds: number[];
    issuedOn: string;
    place?: string;
    holderRole?: InventoryLoanHolderRole;
    authorizationReference?: string;
    authorizationDate?: string;
    associationSignatoryName?: string;
    signatureMode?: InventoryLoanSignatureMode;
    associationSignerId?: string;
    /** Indirizzo del genitore o tutore che firma elettronicamente dal link. */
    holderEmail?: string;
}

export interface InventoryLoanSheetCloseRequest {
    outcome: InventoryLoanReturnOutcome;
    chargeAmount?: number | null;
    chargeDueDate?: string;
    receivedByName?: string;
}

export interface InventoryRenewalRequest {
    newExpirationDate: string;
    authorizationReference: string;
    authorizationDate: string;
    renewedOn?: string;
}

export interface InventorySubstitutionRequest {
    itemId: number;
    quantity: number;
    expirationDate?: string;
    accessories?: string;
    description?: string;
    returnCondition?: InventoryCondition;
    returnNotes?: string;
}

export interface InventoryLoanDurations {
    minMonths: number;
    maxMonths: number;
    renewalMaxMonths: number;
    totalMaxMonths: number;
}

export interface InventoryLoanTemplateFile {
    fileName: string;
    content: string;
}

/** An outcome of the template check: the message is `inventory.loanTemplate.issue.{code}` in the error catalog. */
export interface InventoryLoanTemplateIssue extends ApiErrorIssue {
    line?: number;
    blocking: boolean;
}

export interface InventoryLoanTemplateCheck {
    valid: boolean;
    issues: InventoryLoanTemplateIssue[];
}

export interface InventoryLoanTemplateVersion {
    id: number;
    versionNumber?: number;
    origin: InventoryLoanTemplateOrigin;
    defaultLabel?: string;
    fileName: string;
    contentHash: string;
    createdAt: string;
    active: boolean;
}

export interface InventoryLoanTemplateVariable {
    name: string;
    type: InventoryLoanTemplateVariableType;
    description: string;
    example?: string;
    fields: InventoryLoanTemplateVariable[];
}

export interface InventoryLoanTemplateVariableGroup {
    label: string;
    description: string;
    variables: InventoryLoanTemplateVariable[];
}

export interface InventoryLoanTemplateOverview {
    /** Modello caricato in uso; assente quando i nuovi fogli usano il modello predefinito. */
    active?: InventoryLoanTemplateVersion;
    defaultLabel: string;
    durations: InventoryLoanDurations;
    settingsVersion: number;
    versions: InventoryLoanTemplateVersion[];
    variables: InventoryLoanTemplateVariableGroup[];
    limits: { maxFileBytes: number };
    /** Firma elettronica del foglio e della presa visione, con i requisiti dell'installazione. */
    signature: SignatureSettings;
}

export interface InventoryErasureRequest {
    id: number;
    userIndex: number;
    displayName: string;
    email?: string;
    status: 'PENDING_INVENTORY_RESOLUTION' | 'COMPLETED';
    requestedAt: string;
}

export interface InventoryAdminSummary {
    registeredItems: number;
    totalQuantity: number;
    assignedQuantity: number;
    availableQuantity: number;
    pendingDecisions: number;
    pendingReturns: number;
}

export interface InventoryUserSummary {
    possessedItems: number;
    outstandingQuantity: number;
    pendingDecisions: number;
    lastAssignedAt?: string | null;
}

export type InventoryLabelLayout = 'SINGLE_62X40' | 'A4_GRID_3X8';
export type InventoryIssueSeverity = 'MINOR' | 'LIMITING' | 'UNSAFE';
export type InventoryIssueStatus = 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED' | 'DISMISSED';
export type InventoryScanAction = 'ASSIGN' | 'RETURN' | 'ADD_ITEM_PHOTO' | 'REPORT_ISSUE' | 'PRINT_LABEL' | 'ROTATE_CODE' | 'VIEW' | 'REQUEST_RETURN' | 'COMPLETE_DECISION' | 'ADD_RETURN_PHOTO' | 'ADD_ISSUE_PHOTO';

export interface InventoryLabelRequest {
    layout: InventoryLabelLayout;
    startCell: number;
    showCutMarks: boolean;
    entries: Array<{ itemId: number; copies: number }>;
}

export interface InventoryIssuePhoto {
    id: number;
    fileName: string;
    contentType: string;
    fileSize: number;
    displayOrder: number;
}

export interface InventoryIssue {
    id: number;
    itemId: number;
    assignmentId?: number;
    inventoryNumber: string;
    itemName: string;
    reportedQuantity: number;
    severity: InventoryIssueSeverity;
    description: string;
    status: InventoryIssueStatus;
    resolutionNotes?: string;
    reportedAt: string;
    acknowledgedAt?: string;
    resolvedAt?: string;
    version: number;
    photos: InventoryIssuePhoto[];
}

export interface InventoryScanAssignment {
    assignmentId: number;
    inventoryNumber: string;
    itemName: string;
    conditionStatus?: InventoryCondition;
    outstandingQuantity: number;
    status: InventoryAssignmentStatus;
    expirationDate?: string | null;
    decisionPending: boolean;
    previewPhotoId?: number;
    allowedActions: InventoryScanAction[];
}

export interface InventoryScanResult {
    target: 'ADMIN_ITEM' | 'OWN_ASSIGNMENTS';
    itemId?: number;
    inventoryNumber?: string;
    name?: string;
    conditionStatus?: InventoryCondition;
    totalQuantity?: number;
    assignedQuantity?: number;
    availableQuantity?: number;
    previewPhotoId?: number;
    openIssueCount?: number;
    unsafe?: boolean;
    overdueAssignments?: number;
    expiringAssignments?: number;
    expiringWithinDays?: number;
    pendingDecisions?: number;
    allowedActions: InventoryScanAction[];
    assignments: InventoryScanAssignment[];
}
