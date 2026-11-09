import { Page } from '@playwright/test';
import { environmentMissing, expect, goToSection, mainMenu, openNavigation, signIn, test } from '../fixtures/session';

/**
 * I percorsi del workspace Parti con un browser vero: tastiera, trascinamento fra parti,
 * protezione della bozza e vista mobile.
 *
 * Le prove lavorano su una traccia che ha già almeno due parti e almeno due pagine, perché il
 * workspace non crea pagine: nascono dall'elaborazione di un PDF. Senza una traccia così la prova
 * si salta, come fa il resto della suite quando l'ambiente non c'è.
 *
 * Il gate di prestazioni della specifica (200 parti e 1 000 pagine) ha bisogno di una traccia
 * preparata a parte: va indicata in `E2E_LARGE_TRACK_ID`, altrimenti quella prova si salta. La
 * misura delle stesse dimensioni sulle operazioni e sulla virtualizzazione vive invece nei test di
 * componente, che girano con `npm run test:coverage`.
 */
test.describe('workspace delle parti', () => {
    test.skip(environmentMissing(), 'Servono due tenant di prova: vedi e2e/README.md');

    const openWorkspace = async (page: Page, trackId?: string) => {
        const list = page.getByRole('listbox', { name: /parti della traccia/i });
        // Senza parti il workspace mostra lo stato vuoto: la prova si salta nel passo dopo, non qui.
        const workspaceShown = () => expect(list.or(page.getByText(/nessuna parte/i)).first()).toBeVisible();
        if (trackId) {
            await page.goto(`/tracks/${trackId}?tab=parts`);
            await workspaceShown();
            return;
        }
        await goToSection(page, /tracce/i);
        // Finché la dashboard resta a schermo, il primo link a una traccia è quello delle tracce recenti.
        await expect(page.getByRole('heading', { level: 1, name: /^tracce$/i })).toBeVisible();
        const rows = page.locator('a[href^="/tracks/"]:visible');
        await expect(rows.first()).toBeVisible();
        // I nomi delle tracce dipendono dall'ambiente: si apre la prima dell'elenco che ha parti, altrimenti l'ultima.
        const tracks = [...new Set(await rows.evaluateAll((links) => links.map((link) => link.getAttribute('href')!)))];
        for (const track of tracks) {
            await page.goto(`${track}?tab=parts`);
            await workspaceShown();
            if (await list.isVisible()) return;
        }
    };

    // Sotto 768 px elenco e dettaglio non stanno più affiancati e il divisore non c'è.
    const sideBySide = (page: Page) => (page.viewportSize()?.width ?? 0) >= 768;

    test('l’elenco delle parti si percorre con le frecce e Maiusc estende la selezione', async ({ page, environment }) => {
        const [first] = environment;
        test.skip(!first.users.archivist, 'Serve un archivista del primo tenant');

        await signIn(page, first.users.archivist!, 'archivist');
        await openWorkspace(page);
        const parts = page.getByRole('option');
        test.skip((await parts.count()) < 2, 'Serve una traccia con almeno due parti');

        await parts.first().focus();
        await page.keyboard.press('ArrowDown');
        await expect(parts.nth(1)).toBeFocused();

        await page.keyboard.press('Shift+ArrowUp');
        await expect(page.getByText(/2 parti selezionate/i).first()).toBeVisible();

        await page.keyboard.press('Escape');
        await expect(page.getByRole('button', { name: /unisci parti/i })).toHaveCount(0);
    });

    test('una pagina si trascina sulla parte di destinazione', async ({ page, environment }) => {
        const [first] = environment;
        test.skip(!first.users.archivist, 'Serve un archivista del primo tenant');
        test.skip(!sideBySide(page), 'Sotto 768 px pagine e parti non sono visibili insieme');

        await signIn(page, first.users.archivist!, 'archivist');
        await openWorkspace(page);
        const parts = page.getByRole('option');
        const handle = page.locator('.page-handle').first();
        test.skip((await parts.count()) < 2 || (await handle.count()) === 0, 'Serve una traccia con due parti e almeno una pagina');

        const pagesBefore = await page.locator('.page-card').count();
        await handle.dragTo(parts.nth(1));

        await expect(page.getByText(/pagina spostata nella parte|pagine spostate nella parte/i)).toHaveCount(1);
        await expect(page.getByRole('button', { name: /salva traccia/i })).toBeEnabled();
        expect(await page.locator('.page-card').count()).not.toBe(pagesBefore);
    });

    test('il divisore dei pannelli si muove da tastiera', async ({ page, environment }) => {
        const [first] = environment;
        test.skip(!first.users.archivist, 'Serve un archivista del primo tenant');
        test.skip(!sideBySide(page), 'Sotto 768 px il divisore non c’è');

        await signIn(page, first.users.archivist!, 'archivist');
        await openWorkspace(page);
        test.skip((await page.getByRole('option').count()) < 1, 'Serve una traccia con almeno una parte');

        const divider = page.getByRole('separator', { name: /larghezza dell/i });
        await divider.focus();
        const before = Number(await divider.getAttribute('aria-valuenow'));
        await page.keyboard.press('ArrowLeft');

        // Angular ridisegna al frame successivo (eventCoalescing): l'asserzione aspetta il valore, non lo legge subito.
        await expect(divider).toHaveAttribute('aria-valuenow', String(before - 2));
    });

    test('uscire con una bozza modificata chiede conferma', async ({ page, environment }) => {
        const [first] = environment;
        test.skip(!first.users.archivist, 'Serve un archivista del primo tenant');

        await signIn(page, first.users.archivist!, 'archivist');
        await openWorkspace(page);
        await page.getByRole('button', { name: /nuova parte/i }).first().click();
        await expect(page.getByText(/modifiche non salvate/i).first()).toBeVisible();

        await openNavigation(page);
        await mainMenu(page).getByRole('link', { name: /tracce/i }).click();

        // La conferma di PrimeNG è un alertdialog, e la pagina ne tiene più d'uno in attesa nel DOM.
        await expect(page.getByRole('alertdialog').filter({ hasText: /modifiche non salvate/i })).toBeVisible();
    });

    test('sotto 768 px il dettaglio della parte è una vista a sé', async ({ page, environment }) => {
        const [first] = environment;
        test.skip(!first.users.archivist, 'Serve un archivista del primo tenant');

        await page.setViewportSize({ width: 390, height: 844 });
        await signIn(page, first.users.archivist!, 'archivist');
        await openWorkspace(page);
        test.skip((await page.getByRole('option').count()) < 1, 'Serve una traccia con almeno una parte');

        await page.getByRole('option').first().click();
        // Il nome comprende il glifo dell'icona di p-button, che PrimeNG non nasconde con aria-hidden.
        const backToParts = page.getByRole('button', { name: /^\W*parti$/i });
        await expect(backToParts).toBeVisible();
        await backToParts.click();
        await expect(page.getByRole('listbox', { name: /parti della traccia/i })).toBeVisible();
    });

    test('con 200 parti l’elenco resta scorrevole e virtualizzato', async ({ page, environment }) => {
        const [first] = environment;
        const largeTrack = process.env['E2E_LARGE_TRACK_ID'];
        test.skip(!first.users.archivist, 'Serve un archivista del primo tenant');
        test.skip(!largeTrack, 'Serve una traccia con 200 parti e 1 000 pagine in E2E_LARGE_TRACK_ID');

        await signIn(page, first.users.archivist!, 'archivist');
        const started = Date.now();
        await openWorkspace(page, largeTrack);
        const elapsed = Date.now() - started;

        // Virtualizzato: nel DOM vivono le righe visibili, non duecento.
        expect(await page.getByRole('option').count()).toBeLessThan(60);
        expect(elapsed).toBeLessThan(10_000);

        await page.getByPlaceholder(/cerca per parte o strumento/i).fill('Parte 137');
        await expect(page.getByRole('option')).toHaveCount(1);
    });
});
