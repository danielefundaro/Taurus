import { SignatureActStatus, SignatureActType, SignatureDocumentRole, SignatureEvidenceMethod, SignaturePurpose, SignerRole, SignerStatus } from '../module/signature.module';

type TagSeverity = 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast';

const actTypeLabels: Record<SignatureActType, string> = {
    LOAN_SHEET_ISSUE: 'Emissione',
    LOAN_SHEET_VARIATION: 'Variazione',
    LOAN_SHEET_RETURN: 'Riconsegna',
    ASSIGNMENT_ACKNOWLEDGMENT: 'Presa visione'
};

const actStatusLabels: Record<SignatureActStatus, string> = {
    AWAITING_DATA: 'In attesa dei dati',
    AWAITING_SIGNATURES: 'Da firmare',
    SIGNED: 'Firmato',
    SIGNED_ON_PAPER: 'Firmato su carta',
    CANCELLED: 'Annullato'
};

const actStatusSeverities: Record<SignatureActStatus, TagSeverity> = {
    AWAITING_DATA: 'warn',
    AWAITING_SIGNATURES: 'warn',
    SIGNED: 'success',
    SIGNED_ON_PAPER: 'info',
    CANCELLED: 'secondary'
};

const signerRoleLabels: Record<SignerRole, string> = {
    HOLDER: 'Affidatario',
    MINOR: 'Socio minorenne',
    ASSOCIATION: 'Per l’Associazione',
    ASSIGNEE: 'Assegnatario'
};

const signerStatusLabels: Record<SignerStatus, string> = {
    PENDING: 'In attesa',
    SIGNED: 'Firmato',
    SIGNED_ON_PAPER: 'Firmato su carta'
};

const purposeLabels: Record<SignaturePurpose, string> = {
    ACCEPTANCE: 'Accettazione',
    CLAUSE_APPROVAL: 'Approvazione delle clausole',
    MINOR_ACKNOWLEDGMENT: 'Presa visione del socio minorenne',
    ASSOCIATION_SIGNATURE: 'Firma per l’Associazione',
    ASSIGNMENT_DECISION: 'Presa visione',
    PAPER_DECLARATION: 'Dichiarazione di firma su carta'
};

const methodLabels: Record<SignatureEvidenceMethod, string> = {
    EMAIL_OTP: 'Codice via e-mail',
    TOTP: 'App di autenticazione',
    CIESIGN: 'CieSign',
    PAPER_DECLARATION: 'Dichiarazione su carta'
};

const documentLabels: Record<SignatureDocumentRole, string> = {
    MAIN: 'Documento dell’atto',
    CLAUSES: 'Approvazione delle clausole',
    SNAPSHOT: 'Dati dell’assegnazione'
};

export const signatureActTypeLabel = (type: SignatureActType): string => actTypeLabels[type];
export const signatureActStatusLabel = (status: SignatureActStatus): string => actStatusLabels[status];
export const signatureActStatusSeverity = (status: SignatureActStatus): TagSeverity => actStatusSeverities[status];
export const signerRoleLabel = (role: SignerRole): string => signerRoleLabels[role];
export const signerStatusLabel = (status: SignerStatus): string => signerStatusLabels[status];
export const signaturePurposeLabel = (purpose: SignaturePurpose): string => purposeLabels[purpose];
export const signatureMethodLabel = (method: SignatureEvidenceMethod): string => methodLabels[method];
export const signatureDocumentLabel = (role: SignatureDocumentRole): string => documentLabels[role];

/** Esito del controllo di revoca del certificato di una firma CieSign. */
export const signatureRevocationLabel = (check?: string): string => (check === 'GOOD' ? 'Certificato non revocato' : check === 'UNAVAILABLE' ? 'Elenco di revoca non raggiungibile: revoca non verificata' : '');

/** Percorso della pagina pubblica di firma del genitore o tutore, aperta dal link senza login. */
export const PUBLIC_SIGNATURE_PATH = '/firma/';

/** Se la pagina aperta è quella pubblica di firma: l'applicazione non avvia il login e non carica i dati del tenant. */
export const publicSignaturePage = (path: string = globalThis.location?.pathname ?? ''): boolean => path.startsWith(PUBLIC_SIGNATURE_PATH);

/** Prime cifre di un'impronta SHA-256, per riconoscere un documento a colpo d'occhio. */
export const shortHash = (hash?: string): string => (hash ? `${hash.slice(0, 8)}…${hash.slice(-4)}` : '');
