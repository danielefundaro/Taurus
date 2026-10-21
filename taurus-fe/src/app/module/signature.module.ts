import type { InventoryDecisionType, InventoryLoanHolderRole, InventoryLoanTemplateIssue } from './inventory.module';

export type SignaturePurpose = 'ACCEPTANCE' | 'CLAUSE_APPROVAL' | 'MINOR_ACKNOWLEDGMENT' | 'ASSOCIATION_SIGNATURE' | 'ASSIGNMENT_DECISION' | 'PAPER_DECLARATION';
export type SignatureMethod = 'EMAIL_OTP' | 'TOTP';
export type SignatureActType = 'LOAN_SHEET_ISSUE' | 'LOAN_SHEET_VARIATION' | 'LOAN_SHEET_RETURN' | 'ASSIGNMENT_ACKNOWLEDGMENT';
export type SignatureActStatus = 'AWAITING_DATA' | 'AWAITING_SIGNATURES' | 'SIGNED' | 'SIGNED_ON_PAPER' | 'CANCELLED';
export type SignatureDocumentRole = 'MAIN' | 'CLAUSES' | 'SNAPSHOT';
export type SignerRole = 'HOLDER' | 'MINOR' | 'ASSOCIATION' | 'ASSIGNEE';
export type SignerStatus = 'PENDING' | 'SIGNED' | 'SIGNED_ON_PAPER';
export type SignatureEvidenceMethod = 'EMAIL_OTP' | 'TOTP' | 'CIESIGN' | 'PAPER_DECLARATION';

/**
 * Metodi di conferma disponibili; `enabled` indica se l'associazione richiede la firma elettronica. `preference` è il metodo
 * scelto nel profilo, `proposed` quello che la finestra di firma propone: il codice via e-mail finché l'app di
 * autenticazione non è configurata. `totpAvailable` è falso quando l'app non si può usare in questo momento.
 */
export interface SignatureMethods {
    enabled: boolean;
    preference: SignatureMethod;
    proposed: SignatureMethod;
    totpAvailable: boolean;
    totpConfigured: boolean;
    maskedEmail?: string;
}

/**
 * Elemento confermato da un codice. Presa visione: assegnazione, revisione vista e scelta. Atti del foglio: atto, documento
 * e impronta mostrata al firmatario. Dichiarazione di firma su carta: atto e nota.
 */
export interface SignatureChallengeItem {
    assignmentId?: number;
    revisionHash?: string;
    decision?: InventoryDecisionType;
    rejectionReason?: string;
    actId?: number;
    documentRole?: SignatureDocumentRole;
    documentHash?: string;
    note?: string;
}

export interface SignatureChallengeRequest {
    purpose: SignaturePurpose;
    method: SignatureMethod;
    items: SignatureChallengeItem[];
}

/** Firma da confermare con un codice: il metodo lo sceglie la finestra del codice. */
export type SignatureCodeRequest = Omit<SignatureChallengeRequest, 'method'>;

/** Conferma aperta: dove è stato inviato il codice, il riferimento stampato nell'e-mail e le dichiarazioni confermate. */
export interface SignatureChallenge {
    id: number;
    purpose: SignaturePurpose;
    method: SignatureMethod;
    maskedEmail?: string;
    reference: string;
    expiresAt: string;
    resendAvailableAt?: string;
    remainingSends: number;
    remainingAttempts: number;
    declarations: string[];
}

export interface SignatureActOutcome {
    actId: number;
    type: SignatureActType;
    status: SignatureActStatus;
    assignmentId?: number;
}

export interface SignatureConfirmation {
    challengeId: number;
    acts: SignatureActOutcome[];
}

/** Requisiti dell'installazione: server di posta, chiave dei codici e certificato del sigillo. */
export interface SignatureRequirements {
    mail: boolean;
    codes: boolean;
    seal: boolean;
}

/** Impostazioni della firma del tenant; `templateIssues` rende il modello in uso non firmabile. */
export interface SignatureSettings {
    enabled: boolean;
    requirements: SignatureRequirements;
    templateIssues: InventoryLoanTemplateIssue[];
}

export interface SignatureChainVerification {
    valid: boolean;
    checked: number;
    firstInvalidSequence?: number;
    problem?: 'sequence' | 'previousHash' | 'evidenceHash' | 'personalData' | 'head';
}

/**
 * Dati delle sezioni 1 e 2 inseriti dall'Affidatario prima di firmare l'emissione. Il genitore o tutore, senza account,
 * inserisce anche nome, cognome e data di nascita propri e il luogo di nascita del socio minorenne.
 */
export interface SignatureHolderData {
    birthPlace: string;
    residence: string;
    postalCode: string;
    cityProvince: string;
    taxCode?: string;
    noTaxCode: boolean;
    document: string;
    documentIssuedBy: string;
    documentIssuedOn: string;
    phone?: string;
    minorBirthPlace?: string;
    holderFirstName?: string;
    holderLastName?: string;
    holderBirthDate?: string;
}

export interface SignatureActDocument {
    role: SignatureDocumentRole;
    frozenHash: string;
    sealedHash?: string;
    available: boolean;
}

/**
 * Ultimo link inviato a un firmatario senza account: `sentAt` manca finché il server di posta non ha accettato l'e-mail,
 * `active` è falso quando il link è scaduto o revocato.
 */
export interface SignatureLinkView {
    fingerprint: string;
    sentAt?: string;
    expiresAt: string;
    lastUsedAt?: string;
    active: boolean;
}

/** Firmatario di un atto; il genitore o tutore non ha account e riceve i link all'indirizzo mascherato. */
export interface SignatureActSigner {
    id: number;
    role: SignerRole;
    userId?: string;
    displayName: string;
    required: boolean;
    status: SignerStatus;
    signedAt?: string;
    signedPurposes: SignaturePurpose[];
    maskedEmail?: string;
    link?: SignatureLinkView;
}

/**
 * Atto di un foglio di affidamento: `pendingPurposes` sono le conferme attese dall'utente autenticato e
 * `cieSignAvailable` dice se, come Affidatario con codice fiscale italiano, può firmarle con CieSign.
 */
export interface SignatureAct {
    id: number;
    type: SignatureActType;
    status: SignatureActStatus;
    sheetId?: number;
    sheetNumber?: string;
    holderRole?: InventoryLoanHolderRole;
    operation: string;
    createdAt: string;
    completedAt?: string;
    purgeAfter?: string;
    purgedAt?: string;
    paperNote?: string;
    documents: SignatureActDocument[];
    signers: SignatureActSigner[];
    attestationAvailable: boolean;
    associationSignerChangeable: boolean;
    dataRequired: boolean;
    /** Data di nascita del socio nel profilo (`yyyy-MM-dd`), solo finché servono i dati: senza, i dati non si salvano. */
    memberBirthDate?: string;
    /** Per l'Affidatario che deve ancora firmare: consegne del foglio di cui il socio non ha preso visione. */
    pendingAcknowledgments?: number;
    pendingPurposes: SignaturePurpose[];
    cieSignAvailable: boolean;
    entityVersion: number;
}

export interface SignatureAssociationSignerOption {
    userId: string;
    displayName: string;
}

export interface SignatureEvidence {
    sequence: number;
    purpose: SignaturePurpose;
    method: SignatureEvidenceMethod;
    signerRole?: SignerRole;
    signerName?: string;
    maskedEmail?: string;
    declaration?: string;
    documentRole?: SignatureDocumentRole;
    documentHash?: string;
    signedAt: string;
    ipAddress?: string;
    userAgent?: string;
    certificateSubject?: string;
    certificateIssuer?: string;
    certificateSerial?: string;
    certificateTaxCode?: string;
    revocationCheck?: 'GOOD' | 'UNAVAILABLE';
    evidenceHash: string;
    purged: boolean;
}

/**
 * Atto aperto da un link di firma, come lo mostra la pagina pubblica: associazione, socio minorenne per cui firma il
 * genitore, indirizzo dei codici e scadenza del link.
 */
export interface SignatureLinkAct {
    tenantName: string;
    minorName: string;
    maskedEmail?: string;
    linkExpiresAt: string;
    act: SignatureAct;
}

/** Firma elettronica nella proposta di emissione di un foglio. */
export interface SignatureIssueOptions {
    enabled: boolean;
    templateSignable: boolean;
    associationSigners: SignatureAssociationSignerOption[];
    defaultAssociationSignerId?: string;
}
