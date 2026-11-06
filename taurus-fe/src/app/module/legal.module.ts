export type LegalDocumentType = 'TERMS' | 'PRIVACY';
export type LegalDocumentAction = 'ACCEPT' | 'ACKNOWLEDGE';
/** Bozza finché non è pubblicata, poi in vigore o ritirata. */
export type LegalDocumentState = 'DRAFT' | 'ACTIVE' | 'RETIRED';

export interface LegalDocumentStatus {
    id: number;
    documentType: LegalDocumentType;
    version: string;
    title: string;
    /** Solo per un documento pubblicato prima che il testo stesse in Taurus. */
    url?: string | null;
    contentSha256?: string | null;
    changeSummary?: string | null;
    action: LegalDocumentAction;
    publishedAt: string;
    required: boolean;
    accepted: boolean;
    acceptedAt?: string;
    /** L'utente aveva confermato, nel tenant, un'altra versione dello stesso documento. */
    previousVersionAccepted: boolean;
}

export interface LegalStatus {
    compliant: boolean;
    documents: LegalDocumentStatus[];
}

export interface LegalAcceptanceRequest {
    documentIds: number[];
}

/** Una versione in «Documenti legali». Stato, data, impronta e indirizzo li decide il server. */
export interface LegalDocument {
    id?: number;
    documentType: LegalDocumentType;
    version: string;
    title: string;
    action: LegalDocumentAction;
    required: boolean;
    /** Testo Markdown; l'elenco non lo riporta. */
    content?: string | null;
    changeSummary?: string | null;
    state?: LegalDocumentState;
    publishedAt?: string | null;
    active?: boolean;
    contentSha256?: string | null;
    url?: string | null;
}

export interface LegalPreview {
    html: string;
}

export interface PublicLegalDocumentVersion {
    version: string;
    title: string;
    publishedAt: string;
    active: boolean;
}

/** Una versione pubblicata, come la legge chiunque senza login. */
export interface PublicLegalDocument {
    documentType: LegalDocumentType;
    version: string;
    title: string;
    action: LegalDocumentAction;
    publishedAt: string;
    active: boolean;
    changeSummary?: string | null;
    /** Testo già convertito e ripulito dal server; vuoto per un documento con il solo indirizzo. */
    html?: string | null;
    contentSha256?: string | null;
    url?: string | null;
    /** Le versioni pubblicate dello stesso documento, dalla più recente. */
    versions: PublicLegalDocumentVersion[];
}
