import AxeBuilder from '@axe-core/playwright';
import { Page } from '@playwright/test';
import { environmentMissing, expect, signIn, test } from '../fixtures/session';

/**
 * Gate di accessibilità: axe-core sulle pagine principali, con le regole WCAG 2.1 A e AA.
 *
 * <p>Lo scanner trova le violazioni automatiche, non tutte: la verifica periodica a mano con
 * tastiera e lettore di schermo resta nel runbook e non è sostituita da questa suite.
 */
test.describe('accessibilità', () => {
    test.skip(environmentMissing(), 'Servono due tenant di prova: vedi e2e/README.md');

    const scan = async (page: Page): Promise<void> => {
        const results = await new AxeBuilder({ page }).withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa']).analyze();
        const violations = results.violations.map((violation) => `${violation.id} (${violation.impact}) su ${violation.nodes.length} elementi`);

        expect(violations, violations.join('\n')).toEqual([]);
    };

    test('la pagina di accesso non ha violazioni automatiche', async ({ page }) => {
        await page.goto('/');

        await scan(page);
    });

    test('le pagine principali di un amministratore non hanno violazioni automatiche', async ({ page, environment }) => {
        const [first] = environment;
        test.skip(!first.users.admin, 'Serve un amministratore del primo tenant');

        await signIn(page, first.users.admin!, 'admin');
        await scan(page);

        for (const section of ['/tracks', '/albums', '/calendar']) {
            await page.goto(section);
            await scan(page);
        }
    });
});
