import AxeBuilder from '@axe-core/playwright';
import { Browser, BrowserContext, Page, Response } from '@playwright/test';
import { enterCredentials, environmentMissing, expect, expectNoHorizontalOverflow, signIn, test } from '../fixtures/session';

interface PublicVersion {
    version: string;
    publishedAt: string;
    active: boolean;
}

interface PublicDocument {
    version: string;
    title: string;
    active: boolean;
    versions: PublicVersion[];
}

interface AdminDocument {
    id: number;
    documentType: 'TERMS' | 'PRIVACY';
    version: string;
    state: 'DRAFT' | 'ACTIVE' | 'RETIRED';
}

/** La risposta dell'API pubblica per un percorso come `terms` o `terms/1.3`. */
const publicResponse = (page: Page, path: string): Promise<Response> => page.waitForResponse((response) => new URL(response.url()).pathname.endsWith(`/legal-documents/v1/${path}`));

/** Aspetta che la pagina pubblica abbia finito di caricare il documento. */
const documentLoaded = async (page: Page): Promise<void> => {
    await expect(page.getByText('Caricamento del documento…')).toHaveCount(0);
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
};

/** Una scheda senza sessione, con le opzioni del profilo in uso. */
const freshContext = (browser: Browser): Promise<BrowserContext> => {
    const { baseURL, locale, timezoneId, ignoreHTTPSErrors } = test.info().project.use;
    return browser.newContext({ baseURL, locale, timezoneId, ignoreHTTPSErrors });
};

/** Conferma ogni documento della pagina di accettazione e torna alla pagina chiesta. */
const confirmPendingDocuments = async (page: Page): Promise<void> => {
    await page.waitForURL('**/legal/accept**');
    const submit = page.getByRole('button', { name: /conferma e continua/i });
    await expect(submit).toBeVisible();
    for (const checkbox of await page.getByRole('checkbox').all()) await checkbox.check();
    await submit.click();
    await page.waitForURL((url) => !url.pathname.startsWith('/legal/accept'));
};

/** Accetta la conferma di PrimeNG con il titolo indicato: la pagina ne tiene più d'una nel DOM. */
const acceptConfirmation = async (page: Page, title: string, action: RegExp): Promise<void> => {
    await page.getByRole('alertdialog').filter({ hasText: title }).getByRole('button', { name: action }).click();
};

/**
 * I documenti legali dentro l'applicativo. Le prove della pagina pubblica leggono soltanto: valgono
 * con qualunque versione in vigore, o con nessuna. Lo scenario di pubblicazione scrive nell'ambiente
 * una versione dei termini che, una volta pubblicata, non si elimina più: si abilita con
 * `E2E_LEGAL_PUBLISH=true` e alla fine rimette in vigore la versione di prima. Ciclo di vita,
 * immutabilità, cache e vincoli sono in `LegalResourceIT` del backend.
 */
test.describe('documenti legali', () => {
    test.skip(environmentMissing(), 'Servono due tenant di prova: vedi e2e/README.md');

    test('un visitatore senza sessione legge i termini in vigore e le versioni precedenti', async ({ page }) => {
        const response = publicResponse(page, 'terms');
        await page.goto('/legal/terms');
        const received = await response;

        // La pagina è pubblica: nessun passaggio da Keycloak.
        await expect(page).toHaveURL(/\/legal\/terms$/);
        if (received.status() === 404) {
            await expect(page.getByText('Documento non disponibile')).toBeVisible();
            return;
        }
        expect(received.headers()['cache-control'] ?? '').toContain('max-age=300');
        const document = (await received.json()) as PublicDocument;
        await documentLoaded(page);
        await expect(page.getByRole('heading', { level: 1, name: document.title })).toBeVisible();
        await expect(page.getByText(`Versione ${document.version} · in vigore dal`)).toBeVisible();

        const older = document.versions.find((item) => item.version !== document.version);
        test.skip(!older, 'I termini hanno una sola versione pubblicata');
        const olderResponse = publicResponse(page, `terms/${encodeURIComponent(older!.version)}`);
        await page
            .getByRole('navigation', { name: 'Altre versioni' })
            .getByRole('link', { name: `Versione ${older!.version}` })
            .click();
        await olderResponse;
        await page.waitForURL(`**/legal/terms/${encodeURIComponent(older!.version)}`);
        await expect(page.getByText('Questa versione non è più in vigore')).toBeVisible();
        await expect(page.locator('meta[name="robots"]')).toHaveAttribute('content', 'noindex');

        await page.getByRole('button', { name: /vai alla versione in vigore/i }).click();
        await page.waitForURL(/\/legal\/terms$/);
        await documentLoaded(page);
        await expect(page.locator('meta[name="robots"]')).toHaveCount(0);
    });

    test('una versione inesistente porta alla versione in vigore', async ({ page }) => {
        await page.goto('/legal/privacy/non-esiste');

        await expect(page.getByText('Versione non trovata')).toBeVisible();
        await page.getByRole('button', { name: /vai alla versione in vigore/i }).click();
        await page.waitForURL(/\/legal\/privacy$/);
        await documentLoaded(page);
    });

    test('la pagina pubblica è accessibile e sta in una colonna a 320 pixel', async ({ page }) => {
        await page.setViewportSize({ width: 320, height: 720 });
        const response = publicResponse(page, 'privacy');
        await page.goto('/legal/privacy');
        await response;
        await documentLoaded(page);
        await page.waitForLoadState('networkidle');

        await expectNoHorizontalOverflow(page, 320);
        const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
        const violations = results.violations.map((violation) => `${violation.id} (${violation.impact}) su ${violation.nodes.length} elementi`);
        expect(violations, violations.join('\n')).toEqual([]);
    });

    test('il piè di pagina apre i due documenti in una nuova scheda', async ({ page, environment }) => {
        const [first] = environment;
        const user = first.users.user ?? first.users.admin;
        test.skip(!user, 'Serve un utente o un amministratore del primo tenant');
        await signIn(page, user!, first.users.user ? 'user' : 'admin');

        const links = page.getByRole('navigation', { name: 'Documenti legali' });
        await expect(links.getByRole('link', { name: 'Termini di utilizzo' })).toHaveAttribute('href', '/legal/terms');
        await expect(links.getByRole('link', { name: 'Informativa privacy' })).toHaveAttribute('href', '/legal/privacy');

        const popup = page.waitForEvent('popup');
        await links.getByRole('link', { name: 'Informativa privacy' }).click();
        const opened = await popup;
        await opened.waitForURL('**/legal/privacy');
        await documentLoaded(opened);
    });

    test('una nuova versione dei termini si conferma al passaggio successivo e resta leggibile senza sessione', async ({ page, browser, environment }) => {
        test.skip(process.env.E2E_LEGAL_PUBLISH !== 'true', 'Pubblica una versione dei termini: si abilita con E2E_LEGAL_PUBLISH=true');
        test.skip(test.info().project.name !== 'desktop', 'La pubblicazione si prova una volta sola, sul profilo desktop');
        const [first] = environment;
        test.skip(!first.users.superAdmin || !first.users.user, 'Servono il super amministratore e un utente del primo tenant');
        test.setTimeout(240_000);

        const version = `e2e-${Date.now()}`;
        const summary = `Prova end to end ${version}.`;
        const contexts: BrowserContext[] = [];

        await signIn(page, first.users.superAdmin!, 'superAdmin');
        const list = page.waitForResponse((response) => new URL(response.url()).pathname.endsWith('/api/legal/documents') && response.request().method() === 'GET');
        await page.goto('/legal-documents');
        const previous = ((await (await list).json()) as AdminDocument[]).find((document) => document.documentType === 'TERMS' && document.state === 'ACTIVE');
        let published: number | undefined;

        try {
            // La bozza si salva prima di pubblicarla: così la pagina ha già il suo indirizzo.
            await page.goto('/legal-documents/new');
            await page.locator('#version').fill(version);
            await page.locator('#title').fill('Termini di utilizzo');
            await page.locator('#changeSummary').fill(summary);
            await page.locator('#content').fill(`## Prova end to end\n\nTesto della versione ${version}.`);
            await expect(page.locator('app-legal-text')).toContainText(`Testo della versione ${version}`);
            await page.getByRole('button', { name: /salva bozza/i }).click();
            await page.waitForURL(/\/legal-documents\/\d+$/);

            const activation = page.waitForResponse((response) => /\/api\/legal\/documents\/\d+\/activation$/.test(new URL(response.url()).pathname) && response.request().method() === 'POST');
            await page.getByRole('button', { name: /pubblica/i }).click();
            await acceptConfirmation(page, `Pubblicare la versione ${version}?`, /pubblica/i);
            const activated = await activation;
            expect(activated.status()).toBe(200);
            published = ((await activated.json()) as AdminDocument).id;
            await expect(page.getByText('Versione pubblicata')).toBeVisible();

            // Al passaggio successivo l'utente legge il testo nella pagina di accettazione e lo conferma.
            const userContext = await freshContext(browser);
            contexts.push(userContext);
            const userPage = await userContext.newPage();
            await enterCredentials(userPage, first.users.user!, 'user');
            await userPage.waitForURL('**/legal/accept**');
            await expect(userPage.locator('.text-box').filter({ hasText: `Testo della versione ${version}` })).toBeVisible();
            if (previous) await expect(userPage.getByText(summary)).toBeVisible();
            await confirmPendingDocuments(userPage);
            await expect(userPage.getByRole('navigation', { name: /menu principale/i })).toBeVisible();

            // Un visitatore senza sessione trova la versione nuova e, fra le altre, quella di prima.
            const visitorContext = await freshContext(browser);
            contexts.push(visitorContext);
            const visitor = await visitorContext.newPage();
            await visitor.goto('/legal/terms');
            await documentLoaded(visitor);
            await expect(visitor.getByText(`Versione ${version} · in vigore dal`)).toBeVisible();
            if (previous) {
                await expect(visitor.getByRole('navigation', { name: 'Altre versioni' }).getByRole('link', { name: `Versione ${previous.version}` })).toBeVisible();
            }
        } finally {
            for (const context of contexts) await context.close();
            // Rimette i termini come li aveva trovati: la versione di prima in vigore, o nessuna.
            // Chi l'aveva confermata resta in regola; il super amministratore passa prima dall'accettazione.
            if (published) {
                await page.goto(`/legal-documents/${previous?.id ?? published}`);
                await confirmPendingDocuments(page);
                if (previous) {
                    await page.getByRole('button', { name: /riattiva/i }).click();
                    await acceptConfirmation(page, `Riattivare la versione ${previous.version}?`, /riattiva/i);
                } else {
                    await page.getByRole('button', { name: /ritira/i }).click();
                    await acceptConfirmation(page, `Ritirare la versione ${version}?`, /ritira/i);
                }
                await expect(page.getByText(/di nuovo in vigore|ritirata/i).first()).toBeVisible();
            }
        }
    });
});
