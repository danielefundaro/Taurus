export type LegalDocumentType = 'TERMS' | 'PRIVACY';
export type LegalDocumentAction = 'ACCEPT' | 'ACKNOWLEDGE';

export interface LegalDocumentStatus {
    id: number;
    documentType: LegalDocumentType;
    version: string;
    title: string;
    url: string;
    action: LegalDocumentAction;
    publishedAt: string;
    required: boolean;
    accepted: boolean;
    acceptedAt?: string;
}

export interface LegalStatus {
    compliant: boolean;
    documents: LegalDocumentStatus[];
}

export interface LegalAcceptanceRequest {
    documentIds: number[];
}

export interface LegalDocument {
    id?: number;
    documentType: LegalDocumentType;
    version: string;
    title: string;
    url: string;
    action: LegalDocumentAction;
    publishedAt?: string;
    active: boolean;
    required: boolean;
}
