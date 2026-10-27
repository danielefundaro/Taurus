import AxeBuilder from '@axe-core/playwright';
import { Locator, Page, Response } from '@playwright/test';
import { Credentials, environmentMissing, expect, Role, signIn, test } from '../fixtures/session';

interface OperationalItem {
    type: string;
    count: number;
    targetPath: string;
}

interface OperationalDashboard {
    status: 'COMPLETE' | 'PARTIAL';
    summary: { groupCount: number };
    items: OperationalItem[];
}

/** Le voci personali: ogni ruolo può riceverle, se il dominio le produce per lui. */
const PERSONAL = ['LEGAL_ACCEPTANCE_REQUIRED', 'INVENTORY_DECISION_REQUIRED', 'INVENTORY_SIGNATURE_REQUIRED', 'INVENTORY_ASSIGNMENTS_EXPIRING'];

/** Le voci che ogni ruolo può ricevere, dalla matrice dei profili della specifica; un elenco vuoto ammette tutte le voci. */
const ALLOWED: Record<Role, string[]> = {
    superAdmin: [],
    admin: [],
    archivist: [...PERSONAL, 'CALENDAR_AVAILABILITY_REQUIRED'],
    user: [...PERSONAL, 'CALENDAR_AVAILABILITY_REQUIRED'],
    external: [...PERSONAL, 'CALENDAR_AVAILABILITY_REQUIRED'],
    treasurer: [...PERSONAL, 'FINANCE_MOVEMENTS_UNRECONCILED']
};

const operationsResponse = (page: Page): Promise<Response> =>
    page.waitForResponse((response) => new URL(response.url()).pathname.endsWith('/api/dashboard/operations') && response.request().method() === 'GET');

/** Entra con l'utente e restituisce la dashboard che il server ha calcolato per lui. */
const openDashboard = async (page: Page, user: Credentials, role: Role): Promise<OperationalDashboard> => {
    const response = operationsResponse(page);
    await signIn(page, user, role);
    const received = await response;
    expect(received.status()).toBe(200);
    expect(received.headers()['cache-control'] ?? '').toContain('no-store');
    const dashboard = (await received.json()) as OperationalDashboard;
    await expect(widget(page)).toBeVisible();
    return dashboard;
};

const widget = (page: Page): Locator => page.locator('app-operations-widget');

/**
 * La sezione «Da fare» per ogni profilo: le voci che il server restituisce restano nella matrice
 * dei ruoli, il widget le mostra tutte e ogni azione apre la pagina già filtrata. I dati dei tenant
 * di prova non sono fissati: le prove verificano invarianti, non conteggi.
 */
test.describe('dashboard operativa', () => {
    test.skip(environmentMissing(), 'Servono due tenant di prova: vedi e2e/README.md');

    for (const role of Object.keys(ALLOWED) as Role[]) {
        test(`il profilo ${role} riceve soltanto le attività del proprio ruolo`, async ({ page, environment }) => {
            const [first] = environment;
            const user = first.users[role];
            test.skip(!user, `Serve un utente ${role} del primo tenant`);

            const dashboard = await openDashboard(page, user!, role);

            if (ALLOWED[role].length > 0) {
                expect(dashboard.items.map((item) => item.type).filter((type) => !ALLOWED[role].includes(type))).toEqual([]);
            }
            expect(dashboard.summary.groupCount).toBe(dashboard.items.length);
            if (dashboard.items.length === 0) {
                await expect(widget(page).getByText('Tutto sotto controllo')).toBeVisible();
            } else {
                await expect(widget(page).getByRole('listitem')).toHaveCount(dashboard.items.length);
            }
            if (dashboard.status === 'PARTIAL') {
                await expect(widget(page).getByText('Alcune attività non sono disponibili')).toBeVisible();
            }
        });
    }

    test('ogni azione di un amministratore apre la pagina già filtrata', async ({ page, environment }) => {
        const [first] = environment;
        test.skip(!first.users.admin, 'Serve un amministratore del primo tenant');

        const dashboard = await openDashboard(page, first.users.admin!, 'admin');
        test.skip(dashboard.items.length === 0, 'Il tenant di prova non ha attività per l’amministratore');

        for (const [index, item] of dashboard.items.entries()) {
            await widget(page).getByRole('listitem').nth(index).getByRole('button').click();
            await expect.poll(() => {
                const url = new URL(page.url());
                return `${url.pathname}${url.search}`;
            }).toBe(item.targetPath);
            if (item.targetPath.startsWith('/calendar')) {
                await expect(page.getByText('Filtro attività applicato')).toBeVisible();
            }
            const reloaded = operationsResponse(page);
            await page.goto('/');
            await reloaded;
        }
    });

    test('il pulsante Aggiorna si usa da tastiera e annuncia l’esito', async ({ page, environment }) => {
        const [first] = environment;
        const role: Role = first.users.user ? 'user' : 'admin';
        test.skip(!first.users[role], 'Serve un utente o un amministratore del primo tenant');

        await openDashboard(page, first.users[role]!, role);
        const refresh = widget(page).getByRole('button', { name: 'Aggiorna' });
        await refresh.focus();
        const reloaded = operationsResponse(page);
        await page.keyboard.press('Enter');
        expect((await reloaded).status()).toBe(200);

        await expect(widget(page).locator('[aria-live="polite"]')).toHaveText('Attività aggiornate.');
        await expect(refresh).toBeEnabled();
    });

    for (const role of ['admin', 'user'] as Role[]) {
        test(`la sezione Da fare di ${role} non ha violazioni automatiche di accessibilità`, async ({ page, environment }) => {
            const [first] = environment;
            test.skip(!first.users[role], `Serve un utente ${role} del primo tenant`);

            await openDashboard(page, first.users[role]!, role);
            await expect(widget(page).locator('[aria-busy="true"]')).toHaveCount(0);
            const results = await new AxeBuilder({ page })
                .include('app-operations-widget')
                .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
                .analyze();
            const violations = results.violations.map((violation) => `${violation.id} (${violation.impact}) su ${violation.nodes.length} elementi`);

            expect(violations, violations.join('\n')).toEqual([]);
        });
    }

    for (const width of [320, 768, 1280]) {
        test(`a ${width} pixel la dashboard non scorre in orizzontale`, async ({ page, environment }) => {
            const [first] = environment;
            const role: Role = first.users.admin ? 'admin' : 'user';
            test.skip(!first.users[role], 'Serve un amministratore o un utente del primo tenant');

            await page.setViewportSize({ width, height: 900 });
            await openDashboard(page, first.users[role]!, role);
            await expect(widget(page).locator('[aria-busy="true"]')).toHaveCount(0);

            const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
            expect(overflow).toBeLessThanOrEqual(0);
            // Ogni azione di riga mantiene un'area di attivazione di almeno 44 pixel.
            for (const button of await widget(page).getByRole('listitem').getByRole('button').all()) {
                const box = await button.boundingBox();
                expect(box?.height ?? 0).toBeGreaterThanOrEqual(44);
                expect((box?.x ?? 0) + (box?.width ?? 0)).toBeLessThanOrEqual(width);
            }
        });
    }
});
