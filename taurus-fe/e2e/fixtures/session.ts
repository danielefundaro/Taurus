import { expect, Page, test as base } from '@playwright/test';

/**
 * I ruoli coperti dalla suite. Ogni ruolo è un utente diverso dell'ambiente di prova, con le
 * credenziali lette dall'ambiente: nel repository non vive nessuna credenziale.
 */
export type Role = 'superAdmin' | 'admin' | 'archivist' | 'user' | 'external' | 'treasurer';

/** Un tenant dell'ambiente di prova: la suite ne usa due per verificare che non si vedano fra loro. */
export interface Tenant {
    code: string;
    users: Partial<Record<Role, Credentials>>;
}

export interface Credentials {
    username: string;
    password: string;
}

const credentials = (tenant: string, role: Role): Credentials | undefined => {
    const prefix = `E2E_${tenant.toUpperCase()}_${role.toUpperCase()}`;
    const username = process.env[`${prefix}_USERNAME`];
    const password = process.env[`${prefix}_PASSWORD`];

    return username && password ? { username, password } : undefined;
};

/** I due tenant della suite, descritti dalle variabili d'ambiente. */
export const tenants = (): Tenant[] =>
    ['first', 'second']
        .map((slot) => ({ slot, code: process.env[`E2E_${slot.toUpperCase()}_TENANT`] }))
        .filter((entry): entry is { slot: string; code: string } => !!entry.code)
        .map(({ slot, code }) => ({
            code,
            users: Object.fromEntries(
                (['superAdmin', 'admin', 'archivist', 'user', 'external', 'treasurer'] as Role[])
                    .map((role) => [role, credentials(slot, role)])
                    .filter(([, value]) => !!value)
            )
        }));

/**
 * Entra nell'applicazione con un utente dell'ambiente di prova. Il percorso attraversa la pagina
 * di accesso di Keycloak e, quando l'utente ha più ruoli nel tenant, la scelta del ruolo.
 */
export const signIn = async (page: Page, user: Credentials, role?: Role): Promise<void> => {
    await page.goto('/');

    const username = page.getByLabel(/username|nome utente|utente/i).first();
    await expect(username).toBeVisible();
    await username.fill(user.username);
    await page.getByLabel(/password/i).first().fill(user.password);
    await page.getByRole('button', { name: /sign in|accedi|entra/i }).click();

    // La scelta del ruolo compare soltanto per gli utenti che ne hanno più di uno nel tenant.
    const roleChoice = page.getByRole('button', { name: roleLabel(role) });
    if (role && (await roleChoice.count()) > 0) await roleChoice.first().click();

    await expect(page.getByRole('navigation').or(page.locator('app-topbar'))).toBeVisible();
};

export const signOut = async (page: Page): Promise<void> => {
    await page.getByRole('button', { name: /esci|logout|profilo/i }).first().click();
    const confirm = page.getByRole('menuitem', { name: /esci|logout/i });
    if ((await confirm.count()) > 0) await confirm.first().click();
};

const roleLabel = (role?: Role): RegExp => {
    switch (role) {
        case 'superAdmin':
            return /super ?amministratore/i;
        case 'admin':
            return /amministratore/i;
        case 'archivist':
            return /archivista/i;
        case 'treasurer':
            return /tesoriere/i;
        case 'external':
            return /esterno/i;
        default:
            return /utente/i;
    }
};

/** I tenant configurati, a disposizione delle prove. */
export const test = base.extend<{ environment: Tenant[] }>({
    environment: async ({}, use) => {
        await use(tenants());
    }
});

/** Vero quando l'ambiente non è configurato: la suite si salta invece di fallire. */
export const environmentMissing = (): boolean => tenants().length < 2;

export { expect };
