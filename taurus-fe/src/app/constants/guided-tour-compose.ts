import { GuidedTourPageStepDefinition, GuidedTourStep, GuidedTourText, TenantFeature } from '../module';
import { RoleEnums } from './role.enums';

// Shared by the catalog of the guided tour and by the catalog of the page guides, both kept out of the initial bundle.

/** The steps that apply to the role and to the features of the tenant, in catalog order, with the texts of the role. */
export function composeSteps(definitions: readonly (GuidedTourPageStepDefinition & { route?: string })[], role: RoleEnums, isEnabled: (feature: TenantFeature) => boolean): GuidedTourStep[] {
    return definitions
        .filter((step) => step.roles.includes(role) && (step.requires ?? []).every(isEnabled))
        .map((step) => ({
            id: step.id,
            route: step.route,
            anchors: step.anchors ?? [],
            mobileAnchors: step.mobileAnchors ?? step.anchors ?? [],
            icon: step.icon,
            title: step.title,
            body: textFor(step.body, role),
            points: (step.points ?? []).filter((point) => (!point.roles || point.roles.includes(role)) && (point.requires ?? []).every(isEnabled)).map((point) => point.text)
        }));
}

export function textFor(text: GuidedTourText, role: RoleEnums): string {
    return typeof text === 'string' ? text : (text[role] ?? text.default);
}
