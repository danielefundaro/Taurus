export type InventoryCondition = 'NEW' | 'EXCELLENT' | 'GOOD' | 'FAIR' | 'TO_REPAIR' | 'OUT_OF_SERVICE';
export type InventoryDecisionType = 'ACCEPTED' | 'REJECTED';
export type InventoryAssignmentStatus = 'ACTIVE' | 'PARTIALLY_RETURNED' | 'RETURNED' | 'CANCELLED';
export type InventoryReturnStatus = 'REQUESTED' | 'COMPLETED' | 'CANCELLED';
export type InventoryAssignmentScope = 'POSSESSED' | 'RETURNED';

export interface InventoryPhoto {
    id: number;
    fileName: string;
    contentType: string;
    fileSize: number;
    displayOrder: number;
    preview: boolean;
    insertDate: string;
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
    assignedQuantity: number;
    returnedQuantity: number;
    outstandingQuantity: number;
    assignedAt: string;
    status: InventoryAssignmentStatus;
    revision: number;
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
}

export interface InventoryAssignmentRequest {
    userIndex: number;
    order: number;
    quantity: number;
    description?: string;
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
