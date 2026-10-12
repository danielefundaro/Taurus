/**
 * What the HTTP interceptor does for an error, besides or instead of the toast:
 * - `renewSession`: no toast; renews the token and repeats the request, ends the session if renewal fails;
 * - `endSession`: toast, then logout, because a new token would not solve it;
 * - `forbiddenPage`: toast, then the `forbidden` page;
 * - `tenantSetup`: no toast; forgets the tenant status and goes back to the root, where the guard opens the initial setup;
 * - `tenantFeatureDisabled`: toast; reloads the tenant features and goes back to the root;
 * - `versionConflict`: toast titled «Dati modificati»;
 * - `notFoundPage`: the `notfound` page for a `GET`, a toast for any other request.
 */
export type ApiErrorBehavior = 'renewSession' | 'endSession' | 'forbiddenPage' | 'tenantSetup' | 'tenantFeatureDisabled' | 'versionConflict' | 'notFoundPage';

/**
 * Registry of the error keys that need more than a toast (docs/application-errors-standard.md, «Registro dei comportamenti»).
 * The texts are in api-error-messages.ts. Keep one entry per line with the key in single quotes.
 */
export const API_ERROR_BEHAVIORS: Readonly<Record<string, ApiErrorBehavior>> = {
    'auth.unauthenticated': 'renewSession',
    'auth.identity.missing': 'endSession',
    'auth.user.inactive': 'endSession',
    'auth.forbidden': 'forbiddenPage',
    'tenant.notProvisioned': 'tenantSetup',
    'tenantFeature.finance.disabled': 'tenantFeatureDisabled',
    'tenantFeature.inventory.disabled': 'tenantFeatureDisabled',
    'tenantFeature.onboardingImport.disabled': 'tenantFeatureDisabled',
    'tenantFeature.externalCalendarFeed.disabled': 'tenantFeatureDisabled',
    'tenantFeature.inventoryQr.disabled': 'tenantFeatureDisabled',
    'tenantFeature.notificationPreferences.disabled': 'tenantFeatureDisabled',
    'tenantFeature.webPushReminders.disabled': 'tenantFeatureDisabled',
    'tenantFeature.eventPreparation.disabled': 'tenantFeatureDisabled',
    'version.conflict': 'versionConflict',
    concurrencyFailure: 'versionConflict',
    'id.notFound': 'notFoundPage'
};

/** Every `*.versionConflict` key is a concurrent modification, including keys added after this registry. */
const VERSION_CONFLICT_SUFFIX = '.versionConflict';
/** Every `tenantFeature.{feature}.disabled` key, including features added after this registry. */
const TENANT_FEATURE_DISABLED = /^tenantFeature\.[a-zA-Z0-9]+\.disabled$/;

/** The behavior registered for an error key, if any. */
export const apiErrorBehavior = (key: string): ApiErrorBehavior | undefined => {
    if (Object.prototype.hasOwnProperty.call(API_ERROR_BEHAVIORS, key)) return API_ERROR_BEHAVIORS[key];
    if (key.endsWith(VERSION_CONFLICT_SUFFIX)) return 'versionConflict';
    if (TENANT_FEATURE_DISABLED.test(key)) return 'tenantFeatureDisabled';
    return undefined;
};
