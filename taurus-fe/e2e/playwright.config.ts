import { defineConfig, devices } from '@playwright/test';

/**
 * Suite end to end full-stack: guida un browser reale contro un'istanza completa di Taurus
 * (frontend, backend, Keycloak, PostgreSQL e RabbitMQ). Prerequisiti, variabili d'ambiente e
 * procedura di avvio sono in `e2e/README.md` e nel Runbook di test.
 */
const baseURL = process.env.E2E_BASE_URL ?? 'http://localhost:4200';

export default defineConfig({
    testDir: './specs',
    outputDir: '../target/e2e',
    // Un ambiente completo è più lento di un test di componente: i passi di login attraversano Keycloak.
    timeout: 90_000,
    expect: { timeout: 15_000 },
    forbidOnly: !!process.env.CI,
    retries: process.env.CI ? 1 : 0,
    workers: 1,
    reporter: process.env.CI ? [['list'], ['html', { outputFolder: '../target/e2e-report', open: 'never' }]] : [['list']],
    use: {
        baseURL,
        locale: 'it-IT',
        timezoneId: 'Europe/Rome',
        ignoreHTTPSErrors: true,
        screenshot: 'only-on-failure',
        trace: 'retain-on-failure',
        video: 'retain-on-failure'
    },
    projects: [
        { name: 'desktop', use: { ...devices['Desktop Chrome'], viewport: { width: 1440, height: 900 } } },
        { name: 'mobile', use: { ...devices['Pixel 5'] } }
    ]
});
