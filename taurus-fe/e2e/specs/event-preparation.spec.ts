import AxeBuilder from '@axe-core/playwright';
import { Page, Request } from '@playwright/test';
import { environmentMissing, expect, Role, signIn, test } from '../fixtures/session';

/** Un evento del primo tenant con `EVENT_PREPARATION` attivo, piano configurato e programma richiesto. */
const eventId = process.env['E2E_PREPARATION_EVENT_ID'];

const apiPaths = (page: Page): string[] => {
    const paths: string[] = [];
    page.on('request', (request: Request) => {
        const path = new URL(request.url()).pathname;
        if (path.startsWith('/api/')) paths.push(path);
    });
    return paths;
};

const openEvent = async (page: Page, role: Role, fragment = ''): Promise<void> => {
    await page.goto(`/calendar/${eventId}${fragment}`);
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    if (role !== 'treasurer') await expect(page.locator('#event-data, #preparation-program').first()).toBeVisible();
};

/**
 * La preparazione dell'evento per ogni ruolo: sezioni e chiamate ammesse, collegamenti delle criticità che spostano il
 * focus senza ricaricare la pagina, alternativa da tastiera al trascinamento con la guardia delle unità modificate,
 * accessibilità e layout a 320 pixel. Le prove non salvano nulla: i dati dell'ambiente restano quelli di partenza.
 * Isolamento fra tenant, limiti, conflitti di versione e prestazioni sono in `EventPreparationIT` del backend.
 */
test.describe('preparazione evento', () => {
    test.skip(environmentMissing() || !eventId, 'Servono due tenant di prova e E2E_PREPARATION_EVENT_ID: vedi e2e/README.md');

    test('un amministratore vede stato, avanzamento e criticità che portano alla sezione', async ({ page, environment }) => {
        const [first] = environment;
        test.skip(!first.users.admin, 'Serve un amministratore del primo tenant');
        await signIn(page, first.users.admin!, 'admin');

        const view = page.waitForResponse((response) => new URL(response.url()).pathname === `/api/calendar-events/${eventId}/preparation`);
        await openEvent(page, 'admin');
        const received = await view;
        expect(received.headers()['cache-control'] ?? '').toContain('no-store');

        const section = page.locator('#preparation');
        await expect(section.getByRole('heading', { name: 'Preparazione operativa' })).toBeVisible();
        await expect(section.getByRole('progressbar', { name: 'Avanzamento della preparazione' })).toHaveAttribute('aria-valuenow', /\d+/);

        const toggle = section.getByRole('button', { name: /mostra criticità/i });
        if ((await toggle.count()) > 0) await toggle.click();
        const anchor = section.locator('#preparation-issue-list a[href*="#"]').first();
        test.skip((await anchor.count()) === 0, 'L’evento di prova non ha criticità con una sezione della pagina');
        const fragment = new URL((await anchor.getAttribute('href'))!, 'http://taurus.local').hash.slice(1);
        await anchor.click();
        await expect(page).toHaveURL(new RegExp(`/calendar/${eventId}#${fragment}$`));
        await expect(page.locator(`#${fragment}`)).toBeFocused();
    });

    test('un archivista riordina da tastiera e la guardia elenca il programma modificato', async ({ page, environment }) => {
        const [first] = environment;
        test.skip(!first.users.archivist, 'Serve un archivista del primo tenant');
        const paths = apiPaths(page);
        await signIn(page, first.users.archivist!, 'archivist');
        await openEvent(page, 'archivist');

        expect(paths).toContain(`/api/calendar-events/${eventId}/preparation/catalogue`);
        expect(paths).not.toContain(`/api/calendar-events/${eventId}/preparation`);
        const program = page.locator('#preparation-program');
        const moveDown = program.getByRole('button', { name: /^sposta dopo/i }).first();
        test.skip((await moveDown.count()) === 0, 'Il programma di prova ha meno di due brani');

        await moveDown.focus();
        await page.keyboard.press('Enter');
        await expect(program.getByText('Da salvare')).toBeVisible();
        await expect(program.locator('[aria-live="polite"]')).toContainText(/spostata in posizione 2/);

        await page.goto('/calendar');
        const dialog = page.getByRole('alertdialog').or(page.getByRole('dialog'));
        await expect(dialog).toContainText(/programma/i);
        await dialog.getByRole('button', { name: /rimani/i }).click();
        await expect(page).toHaveURL(new RegExp(`/calendar/${eventId}`));
    });

    test('un socio legge il programma senza stato di preparazione né strumenti di modifica', async ({ page, environment }) => {
        const [first] = environment;
        test.skip(!first.users.user, 'Serve un utente del primo tenant');
        const paths = apiPaths(page);
        await signIn(page, first.users.user!, 'user');
        await openEvent(page, 'user');

        expect(paths).toContain(`/api/user/calendar-events/${eventId}/preparation`);
        expect(paths.filter((path) => path.startsWith(`/api/calendar-events/${eventId}/preparation`))).toEqual([]);
        await expect(page.getByRole('heading', { name: 'Preparazione operativa' })).toHaveCount(0);
        await expect(page.getByRole('button', { name: 'Salva programma' })).toHaveCount(0);
        await expect(page.getByRole('progressbar')).toHaveCount(0);
    });

    test('il tesoriere resta sulle API economiche', async ({ page, environment }) => {
        const [first] = environment;
        test.skip(!first.users.treasurer, 'Serve un tesoriere del primo tenant');
        const paths = apiPaths(page);
        await signIn(page, first.users.treasurer!, 'treasurer');
        await openEvent(page, 'treasurer');
        await expect(page.locator('#preparation-finance, #preparation').first()).toBeVisible();

        const eventPaths = paths.filter((path) => path.includes(`/${eventId}`));
        expect(eventPaths.length).toBeGreaterThan(0);
        expect(eventPaths.every((path) => path.startsWith('/api/finance/'))).toBeTrue();
        await expect(page.locator('#event-data, #preparation-program, #preparation-materials, #availability, #presence')).toHaveCount(0);
    });

    for (const role of ['admin', 'user'] as const) {
        test(`la pagina è accessibile e non scorre in orizzontale a 320 pixel per il profilo ${role}`, async ({ page, environment }) => {
            const [first] = environment;
            test.skip(!first.users[role], `Serve un utente ${role} del primo tenant`);
            await page.setViewportSize({ width: 320, height: 720 });
            await signIn(page, first.users[role]!, role);
            await openEvent(page, role);

            const overflow = await page.evaluate(() => document.documentElement.scrollWidth - document.documentElement.clientWidth);
            expect(overflow).toBeLessThanOrEqual(0);
            const scope = role === 'admin' ? '#preparation' : '#preparation-program';
            await expect(page.locator(scope)).toBeVisible();
            const audit = await new AxeBuilder({ page }).include(scope).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
            expect(audit.violations).toEqual([]);
        });
    }
});
