import { environmentMissing, expect, goToSection, mainMenu, signIn, signOut, test } from '../fixtures/session';

/**
 * Il giro minimo chiesto dal Runbook di test: ogni ruolo vede soltanto il proprio menu e due
 * tenant non si vedono fra loro, con un browser reale contro l'applicazione completa.
 */
test.describe('ruoli e tenant', () => {
    // Senza i due tenant di prova la suite non ha nulla su cui girare: si salta, non fallisce.
    test.skip(environmentMissing(), 'Servono due tenant di prova: vedi e2e/README.md');

    test('un amministratore vede le sezioni di amministrazione, un utente no', async ({ page, environment }) => {
        const [first] = environment;
        test.skip(!first.users.admin || !first.users.user, 'Servono un amministratore e un utente del primo tenant');

        // Le voci si cercano nel menu: le stesse parole compaiono anche nelle schede della dashboard.
        await signIn(page, first.users.admin!, 'admin');
        await expect(mainMenu(page).getByRole('link', { name: /utenti/i })).toBeVisible();
        await expect(mainMenu(page).getByRole('link', { name: /tracce/i })).toBeVisible();
        await signOut(page);

        await signIn(page, first.users.user!, 'user');
        await expect(mainMenu(page).getByRole('link', { name: /tracce/i })).toBeVisible();
        await expect(mainMenu(page).getByRole('link', { name: /utenti/i })).toHaveCount(0);
    });

    test('il direttivo gestisce l’associazione, ma la configurazione iniziale e le consegne restano all’amministratore', async ({ page, environment }) => {
        const [first] = environment;
        test.skip(!first.users.board, 'Serve un membro del direttivo del primo tenant');

        await signIn(page, first.users.board!, 'board');
        await expect(mainMenu(page).getByRole('link', { name: /utenti/i })).toBeVisible();
        await expect(mainMenu(page).getByRole('link', { name: /calendario/i })).toBeVisible();
        await expect(mainMenu(page).getByRole('link', { name: /strumenti/i })).toBeVisible();
        await expect(mainMenu(page).getByRole('link', { name: /configurazione iniziale/i })).toHaveCount(0);
        await expect(mainMenu(page).getByRole('link', { name: /consegne notifiche/i })).toHaveCount(0);
        await expect(mainMenu(page).getByRole('link', { name: /feed calendario/i })).toHaveCount(0);
    });

    test('il nome del tenant nella topbar è quello dell’utente che ha eseguito l’accesso', async ({ page, environment }) => {
        const [first, second] = environment;
        test.skip(!first.users.admin || !second.users.admin, 'Serve un amministratore in entrambi i tenant');

        await signIn(page, first.users.admin!, 'admin');
        await expect(page.locator('app-topbar')).toContainText(new RegExp(first.code, 'i'));
        await signOut(page);

        await signIn(page, second.users.admin!, 'admin');
        await expect(page.locator('app-topbar')).toContainText(new RegExp(second.code, 'i'));
        await expect(page.locator('app-topbar')).not.toContainText(new RegExp(first.code, 'i'));
    });

    test('una traccia di un tenant non è raggiungibile dall’altro', async ({ page, environment }) => {
        const [first, second] = environment;
        test.skip(!first.users.archivist || !second.users.admin, 'Serve un archivista nel primo tenant e un amministratore nel secondo');

        await signIn(page, first.users.archivist!, 'archivist');
        await goToSection(page, /tracce/i);
        // La riga si sceglie dall'elenco: senza aspettare l'arrivo il clic cadrebbe sulla dashboard.
        await page.waitForURL('**/tracks');
        // I nomi delle tracce dipendono dall'ambiente: la prima riga si trova dal suo indirizzo, e
        // fra i collegamenti della riga solo alcuni si vedono, perché il formato cambia con la larghezza.
        const firstTrack = page.locator('a[href^="/tracks/"]:visible').first();
        await expect(firstTrack).toBeVisible();
        await firstTrack.click();
        // Il clic avvia una navigazione del router: l'indirizzo cambia dopo, non durante.
        await page.waitForURL(/\/tracks\/\d+/);
        const trackUrl = new URL(page.url()).pathname;
        expect(trackUrl).toMatch(/\/tracks\//);
        await signOut(page);

        // Lo stesso indirizzo, con la sessione dell'altro tenant, non deve mostrare la traccia.
        await signIn(page, second.users.admin!, 'admin');
        await page.goto(trackUrl);
        await expect(page.getByText(/non trovat|non disponibil/i).first()).toBeVisible();
    });
});
