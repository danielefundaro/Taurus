export type PreparationProfile = 'PERFORMANCE' | 'REHEARSAL' | 'OTHER';
export type PreparationStatus = 'NOT_CONFIGURED' | 'BLOCKED' | 'ATTENTION' | 'READY' | 'UNKNOWN';
export type ClosureStatus = 'TO_CLOSE' | 'CLOSED_WITH_WARNINGS' | 'CLOSED' | 'NOT_REQUIRED' | 'UNKNOWN';

export interface EventPreparationConfiguration {
    profile: PreparationProfile;
    locationRequired: boolean;
    programRequired: boolean;
    scoresRequired: boolean;
    availabilityRequired: boolean;
    minimumAvailableParticipants?: number;
    availabilityDeadlineMinutes: number;
    materialsRequired: boolean;
    materialsDeadlineMinutes: number;
    budgetRequired: boolean;
    presenceClosureRequired: boolean;
    financialClosureRequired: boolean;
    version: number;
}

export interface EventPreparationIssue {
    code: string;
    area: string;
    severity: 'BLOCKER' | 'WARNING';
    message: string;
    action: string;
}

export interface EventPreparationEvaluation {
    evaluatedAt: string;
    phase: 'PREPARATION' | 'IN_PROGRESS' | 'FOLLOW_UP' | 'UNKNOWN';
    preparationStatus: PreparationStatus;
    closureStatus: ClosureStatus;
    completionPercent: number;
    passedChecks: number;
    applicableChecks: number;
    blockerCount: number;
    warningCount: number;
    issues: EventPreparationIssue[];
}

export interface EventPreparationProgramEntry {
    id: number;
    trackId: number;
    trackName: string;
    trackState: string;
    order: number;
    plannedDurationSeconds?: number;
    notes?: string;
}

export interface EventPreparationMaterial {
    id: number;
    itemId: number;
    itemName: string;
    assignmentId?: number;
    assignee?: string;
    requiredQuantity: number;
    condition: string;
    confirmed: boolean;
    notes?: string;
}

export interface EventPreparationView {
    configuration?: EventPreparationConfiguration;
    evaluation: EventPreparationEvaluation;
    program: EventPreparationProgramEntry[];
    availability: { expected: number; available: number; unavailable: number; missing: number; minimumRequired?: number; deadline?: string };
    materials: EventPreparationMaterial[];
}
