import { LegalDocumentAction, LegalDocumentState, LegalDocumentType } from '../module/legal.module';

type TagSeverity = 'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast';

export const LEGAL_DOCUMENT_TYPES: { label: string; value: LegalDocumentType }[] = [
    { label: 'Termini di utilizzo', value: 'TERMS' },
    { label: 'Informativa privacy', value: 'PRIVACY' }
];

export const LEGAL_DOCUMENT_ACTIONS: { label: string; value: LegalDocumentAction }[] = [
    { label: 'Accettazione', value: 'ACCEPT' },
    { label: 'Presa visione', value: 'ACKNOWLEDGE' }
];

const stateLabels: Record<LegalDocumentState, string> = { DRAFT: 'Bozza', ACTIVE: 'In vigore', RETIRED: 'Ritirata' };
const stateSeverities: Record<LegalDocumentState, TagSeverity> = { DRAFT: 'warn', ACTIVE: 'success', RETIRED: 'secondary' };

/** La versione resta sicura dentro un indirizzo: come la controlla il server. */
export const LEGAL_VERSION_PATTERN = /^[0-9A-Za-z][0-9A-Za-z._-]{0,31}$/;
export const LEGAL_CONTENT_MAX_LENGTH = 100_000;
export const LEGAL_CHANGE_SUMMARY_MAX_LENGTH = 1_000;

export const legalDocumentTypeLabel = (type: LegalDocumentType): string => LEGAL_DOCUMENT_TYPES.find((item) => item.value === type)?.label ?? type;
export const legalDocumentActionLabel = (action: LegalDocumentAction): string => LEGAL_DOCUMENT_ACTIONS.find((item) => item.value === action)?.label ?? action;
export const legalDocumentStateLabel = (state?: LegalDocumentState): string => (state ? stateLabels[state] : '');
export const legalDocumentStateSeverity = (state?: LegalDocumentState): TagSeverity => (state ? stateSeverities[state] : 'secondary');
