import { HttpErrorResponse } from '@angular/common/http';
import { ClosureStatus, EventPreparationConfiguration, EventPreparationEvaluation, EventPreparationIssue, PreparationArea, PreparationProfile, PreparationSeverity, PreparationStatus } from '../../../../module';

export type TagSeverity = 'success' | 'warn' | 'danger' | 'secondary' | 'info';

/** The requirement flags of the configuration, in the order the form lists them. */
export type RequirementKey = 'locationRequired' | 'programRequired' | 'scoresRequired' | 'availabilityRequired' | 'materialsRequired' | 'budgetRequired' | 'presenceClosureRequired' | 'financialClosureRequired';

export const MAX_PREPARATION_ROWS = 100;

export const PREPARATION_PROFILES: { label: string; value: PreparationProfile }[] = [
    { label: 'Esibizione o concerto', value: 'PERFORMANCE' },
    { label: 'Prova', value: 'REHEARSAL' },
    { label: 'Altro evento', value: 'OTHER' }
];

export const STATUS_LABELS: Record<PreparationStatus, string> = {
    NOT_CONFIGURED: 'Da configurare',
    BLOCKED: 'Preparazione incompleta',
    ATTENTION: 'Da verificare',
    READY: 'Evento pronto',
    UNKNOWN: 'Valutazione non disponibile'
};

export const CLOSURE_LABELS: Record<ClosureStatus, string> = {
    TO_CLOSE: 'Da completare',
    CLOSED_WITH_WARNINGS: 'Completata con verifiche',
    CLOSED: 'Completata',
    NOT_REQUIRED: 'Non richiesta',
    UNKNOWN: 'Stato non disponibile'
};

export const PHASE_LABELS: Record<EventPreparationEvaluation['phase'], string> = {
    PREPARATION: 'Preparazione',
    IN_PROGRESS: 'In corso',
    FOLLOW_UP: 'Chiusura',
    UNKNOWN: 'Fase non disponibile'
};

export const SEVERITY_LABELS: Record<PreparationSeverity, string> = {
    BLOCKER: 'Bloccante',
    WARNING: 'Da verificare',
    INFO: 'Suggerimento'
};

export const AREA_LABELS: Record<PreparationArea, string> = {
    EVENT: 'Dati evento',
    PROGRAM: 'Programma',
    SCORES: 'Spartiti',
    AVAILABILITY: 'Disponibilità',
    MATERIALS: 'Materiali',
    BUDGET: 'Preventivo',
    PRESENCE: 'Presenze',
    FINANCE: 'Chiusura economica'
};

/** What blocks a material row, by the stable code the backend returns. */
export const MATERIAL_PROBLEM_LABELS: Record<string, string> = {
    ASSIGNMENT_MISSING: 'Nessuna assegnazione collegata',
    ASSIGNMENT_INACTIVE: 'Assegnazione non più attiva',
    ASSIGNMENT_ITEM_MISMATCH: 'Assegnazione di un altro oggetto',
    QUANTITY_INSUFFICIENT: 'Quantità residua insufficiente',
    ITEM_NOT_USABLE: 'Oggetto da riparare o fuori servizio',
    UNSAFE_ISSUE: 'Guasto non sicuro aperto',
    CONFIRMATION_MISSING: 'Preparazione da confermare',
    CONFIRMATION_OBSOLETE: 'Conferma non più valida'
};

/** The requirements whose profile asks for an explicit choice: the form proposes no value for them. */
const EXPLICIT_CHOICES: Record<PreparationProfile, RequirementKey[]> = {
    PERFORMANCE: ['materialsRequired'],
    REHEARSAL: ['locationRequired', 'materialsRequired'],
    OTHER: ['locationRequired', 'availabilityRequired']
};

const SEVERITY_ORDER: Record<PreparationSeverity, number> = { BLOCKER: 0, WARNING: 1, INFO: 2 };

export function statusSeverity(status?: PreparationStatus): TagSeverity {
    if (status === 'READY') return 'success';
    if (status === 'BLOCKED' || status === 'UNKNOWN') return 'danger';
    return status === 'ATTENTION' ? 'warn' : 'secondary';
}

export function issueSeverity(severity: PreparationSeverity): TagSeverity {
    return severity === 'BLOCKER' ? 'danger' : severity === 'WARNING' ? 'warn' : 'info';
}

/** Blockers first, then warnings and suggestions; within a severity the order of the backend is kept. */
export function sortIssues(issues: EventPreparationIssue[]): EventPreparationIssue[] {
    return issues
        .map((issue, index) => ({ issue, index }))
        .sort((left, right) => SEVERITY_ORDER[left.issue.severity] - SEVERITY_ORDER[right.issue.severity] || left.index - right.index)
        .map(({ issue }) => issue);
}

export function isAnchor(action?: string): action is string {
    return !!action && action.startsWith('#');
}

/** The words of the link that takes the reader where an issue is fixed. */
export function actionLabel(action: string): string {
    const anchors: Record<string, string> = {
        '#event-data': 'Vai ai dati evento',
        '#preparation-program': 'Vai al programma',
        '#availability': 'Vai alle disponibilità',
        '#preparation-materials': 'Vai ai materiali',
        '#preparation-confirmations': 'Vai alle conferme',
        '#presence': 'Vai alle presenze',
        '#preparation-finance': 'Vai al consuntivo'
    };
    if (anchors[action]) return anchors[action];
    if (action.startsWith('/tracks/')) return 'Apri la traccia';
    if (action.startsWith('/inventory/items/')) return 'Apri l’oggetto';
    if (action.startsWith('/finance')) return 'Apri i movimenti';
    return 'Apri';
}

/**
 * The starting values of a profile. The requirements of an explicit choice stay `null` until the administrator
 * decides them; a requirement whose module is off cannot be chosen and is not required.
 */
export function profileConfiguration(profile: PreparationProfile, modules: { inventory: boolean; finance: boolean } = { inventory: true, finance: true }): EventPreparationConfiguration {
    const base: Record<RequirementKey, boolean> =
        profile === 'PERFORMANCE'
            ? { locationRequired: true, programRequired: true, scoresRequired: true, availabilityRequired: true, materialsRequired: false, budgetRequired: true, presenceClosureRequired: true, financialClosureRequired: true }
            : profile === 'REHEARSAL'
              ? { locationRequired: false, programRequired: true, scoresRequired: true, availabilityRequired: true, materialsRequired: false, budgetRequired: false, presenceClosureRequired: false, financialClosureRequired: false }
              : { locationRequired: false, programRequired: false, scoresRequired: false, availabilityRequired: false, materialsRequired: false, budgetRequired: false, presenceClosureRequired: false, financialClosureRequired: false };
    const flags: Record<RequirementKey, boolean | null> = { ...base };
    EXPLICIT_CHOICES[profile].forEach((key) => (flags[key] = null));
    if (!modules.inventory) flags.materialsRequired = false;
    if (!modules.finance) {
        flags.budgetRequired = false;
        flags.financialClosureRequired = false;
    }
    return {
        profile,
        ...flags,
        minimumAvailableParticipants: base.availabilityRequired ? 1 : undefined,
        availabilityDeadlineMinutes: 1440,
        materialsDeadlineMinutes: 180,
        version: 0
    };
}

export function isExplicitChoice(profile: PreparationProfile, key: RequirementKey): boolean {
    return EXPLICIT_CHOICES[profile].includes(key);
}

export function formatDuration(seconds?: number): string {
    if (!seconds) return '';
    const minutes = Math.floor(seconds / 60);
    const rest = seconds % 60;
    return `${minutes}:${String(rest).padStart(2, '0')}`;
}

/** A save rejected because another request changed the same unit first. */
export function isVersionConflict(error: unknown): boolean {
    return error instanceof HttpErrorResponse && error.status === 409;
}

export const VERSION_CONFLICT = ['eventPreparation.versionConflict'] as const;
