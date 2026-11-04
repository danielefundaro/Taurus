import { RoleEnums } from '../constants';
import { TenantFeature } from './tenant-features.module';

export type GuidedTourStatus = 'IN_PROGRESS' | 'COMPLETED' | 'SKIPPED';

/** Personal settings in the tenant: `version` is `null` until the profile is written for the first time. */
export interface GuidedTourSettings {
    autoStartEnabled: boolean;
    legacyInvitation: boolean;
    version: number | null;
}

export interface GuidedTourProgress {
    status: GuidedTourStatus;
    seenSteps: string[];
    lastStepId: string | null;
    autoProposals: number;
    catalogRevision: number;
    startedAt: string | null;
    completedAt: string | null;
    version: number;
}

/** The guided tour of the session: `progress` is `null` while the role of the token has no state. */
export interface GuidedTour {
    enabled: boolean;
    settings: GuidedTourSettings;
    role: RoleEnums;
    progress: GuidedTourProgress | null;
}

export interface GuidedTourSettingsRequest {
    autoStartEnabled: boolean;
    version: number | null;
}

export interface GuidedTourProgressRequest {
    status: GuidedTourStatus;
    seenSteps: string[];
    lastStepId: string | null;
    autoProposals: number;
    catalogRevision: number;
    version: number | null;
}

/** A text with optional variants for the roles that must not read the default one. */
export type GuidedTourText = string | ({ default: string } & Partial<Record<RoleEnums, string>>);

export interface GuidedTourPointDefinition {
    text: string;
    /** Tenant features all required by the point. */
    requires?: TenantFeature[];
    /** Roles the point applies to; every role of the step when missing. */
    roles?: RoleEnums[];
}

/** A step of the catalog: the path of a role is the ordered list of the steps that apply to it. */
export interface GuidedTourStepDefinition {
    id: string;
    roles: RoleEnums[];
    requires?: TenantFeature[];
    /** Page opened before showing the step. */
    route?: string;
    /** Candidate anchors in order of preference; the step is centered when none is visible. */
    anchors?: string[];
    /** Candidates below 992 px, when they differ: a page step points at the page header. */
    mobileAnchors?: string[];
    icon: string;
    title: string;
    body: GuidedTourText;
    points?: GuidedTourPointDefinition[];
    introducedIn: number;
}

/** A step composed for the role and the features of the session. */
export interface GuidedTourStep {
    id: string;
    route?: string;
    anchors: string[];
    mobileAnchors: string[];
    icon: string;
    title: string;
    body: string;
    points: string[];
}
