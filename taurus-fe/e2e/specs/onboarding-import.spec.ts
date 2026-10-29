import AxeBuilder from '@axe-core/playwright';
import { Page, Response } from '@playwright/test';
import { environmentMissing, expect, signIn, test } from '../fixtures/session';

interface Limits {
    maxFileSizeBytes: number;
    maxTotalRows: number;
    maxUserRows: number;
}

interface Context {
    tenantName: string;
    availableSections: string[];
    limits: Limits;
    lastImport?: { id: number; status: string };
}

const contextResponse = (page: Page): Promise<Response> => page.waitForResponse((response) => new URL(response.url()).pathname.endsWith('/api/onboarding/context'));

/**
 * La configurazione iniziale vista dal browser. Le prove non caricano nulla e non applicano nulla: un'importazione
 * scriverebbe nell'ambiente di prova dati che nessuna modalità additiva può togliere. Validazione, applicazione,
 * compensazione, limiti e carico stanno in `OnboardingImportIT` del backend.
 */
test.describe('configurazione iniziale', () => {
    test.skip(environmentMissing(), 'Servono due tenant di prova: vedi e2e/README.md');

    test('un amministratore apre la procedura guidata con i limiti del tenant', async ({ page, environment }) => {
        const [first] = environment;
        test.skip(!first.users.admin, 'Serve un amministratore del primo tenant');
        await signIn(page, first.users.admin!, 'admin');

        const response = contextResponse(page);
        await page.goto('/onboarding');
        const received = await response;
        test.skip(received.status() === 403, 'Il tenant di prova non ha la funzionalità di importazione iniziale');
        expect(received.headers()['cache-control'] ?? '').toContain('no-store');
        const context = (await received.json()) as Context;

        await expect(page.getByRole('heading', { level: 1, name: 'Configurazione iniziale' })).toBeVisible();
        await expect(page.getByText(context.tenantName)).toBeVisible();
        await expect(page.getByText('Ultima importazione')).toBeVisible();
        await expect(page.getByText(new RegExp(`${context.limits.maxTotalRows} righe complessive`))).toBeVisible();
        // Nessuno stato tecnico arriva all'utente: la pagina mostra solo etichette italiane.
        expect(await page.locator('body').innerText()).not.toMatch(/\b(UPLOADED|VALIDATING|READY|APPLYING|COMPLETED|FAILED)\b/);
    });

    test('il modello del workbook si scarica senza toccare i dati', async ({ page, environment }) => {
        const [first] = environment;
        test.skip(!first.users.admin, 'Serve un amministratore del primo tenant');
        await signIn(page, first.users.admin!, 'admin');

        const response = contextResponse(page);
        await page.goto('/onboarding');
        test.skip((await response).status() === 403, 'Il tenant di prova non ha la funzionalità di importazione iniziale');

        const download = page.waitForEvent('download');
        await page.getByRole('button', { name: 'Modello XLSX' }).click();
        expect((await download).suggestedFilename()).toBe('taurus-onboarding-v1.xlsx');
    });

    test('un socio non raggiunge la procedura guidata', async ({ page, environment }) => {
        const [first] = environment;
        test.skip(!first.users.user, 'Serve un utente del primo tenant');
        await signIn(page, first.users.user!, 'user');

        await expect(page.getByRole('link', { name: /configurazione iniziale/i })).toHaveCount(0);

        const response = contextResponse(page);
        await page.goto('/onboarding');
        expect((await response).status()).toBe(403);
        await expect(page.getByRole('heading', { level: 1, name: 'Configurazione iniziale' })).toHaveCount(0);
    });

    test('la procedura guidata è accessibile e sta in una riga sola a 320 e 768 pixel', async ({ page, environment }) => {
        const [first] = environment;
        test.skip(!first.users.admin, 'Serve un amministratore del primo tenant');
        await signIn(page, first.users.admin!, 'admin');

        const response = contextResponse(page);
        await page.goto('/onboarding');
        test.skip((await response).status() === 403, 'Il tenant di prova non ha la funzionalità di importazione iniziale');
        await expect(page.getByRole('heading', { level: 1, name: 'Configurazione iniziale' })).toBeVisible();

        for (const width of [320, 768, 1280]) {
            await page.setViewportSize({ width, height: 900 });
            const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
            expect(overflow, `Scorrimento orizzontale a ${width} pixel`).toBeLessThanOrEqual(0);
        }

        const audit = await new AxeBuilder({ page }).include('app-onboarding').withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
        expect(audit.violations).toEqual([]);
    });
});
