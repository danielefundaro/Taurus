import { environmentMissing, expect, signIn, signOut, test } from '../fixtures/session';

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

        await signIn(page, first.users.admin!, 'admin');
        await expect(page.getByRole('link', { name: /utenti/i })).toBeVisible();
        await expect(page.getByRole('link', { name: /tracce/i })).toBeVisible();
        await signOut(page);

        await signIn(page, first.users.user!, 'user');
        await expect(page.getByRole('link', { name: /tracce/i })).toBeVisible();
        await expect(page.getByRole('link', { name: /utenti/i })).toHaveCount(0);
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
        await page.getByRole('link', { name: /tracce/i }).click();
        const firstTrack = page.getByRole('link').filter({ hasNotText: /tracce/i }).first();
        await firstTrack.click();
        const trackUrl = new URL(page.url()).pathname;
        expect(trackUrl).toMatch(/\/tracks\//);
        await signOut(page);

        // Lo stesso indirizzo, con la sessione dell'altro tenant, non deve mostrare la traccia.
        await signIn(page, second.users.admin!, 'admin');
        await page.goto(trackUrl);
        await expect(page.getByText(/non trovat|non disponibil/i).first()).toBeVisible();
    });
});
