import { environment } from './environments/environment';
import { loadRuntimeConfig } from './runtime-config';

describe('loadRuntimeConfig', () => {
    const compiled = { baseUrl: environment.baseUrl, vapidPublicKey: environment.vapidPublicKey, keycloak: { ...environment.keycloak } };

    const serve = (body: unknown, ok = true, status = 200) =>
        spyOn(window, 'fetch').and.resolveTo({ ok, status, json: () => Promise.resolve(body) } as Response);

    afterEach(() => {
        environment.baseUrl = compiled.baseUrl;
        environment.vapidPublicKey = compiled.vapidPublicKey;
        environment.keycloak.baseurl = compiled.keycloak.baseurl;
        environment.keycloak.realm = compiled.keycloak.realm;
        environment.keycloak.clientId = compiled.keycloak.clientId;
    });

    it('applies the served values to the environment', async () => {
        serve({
            baseUrl: 'https://taurus.example.org/api',
            vapidPublicKey: 'chiave-servita',
            keycloak: { baseurl: 'https://auth.example.org', realm: 'banda', clientId: 'web_app' }
        });

        await loadRuntimeConfig();

        expect(environment.baseUrl).toBe('https://taurus.example.org/api');
        expect(environment.vapidPublicKey).toBe('chiave-servita');
        expect(environment.keycloak.baseurl).toBe('https://auth.example.org');
        expect(environment.keycloak.realm).toBe('banda');
    });

    it('keeps the compiled value of every key the file does not declare', async () => {
        serve({ baseUrl: 'https://taurus.example.org/api' });

        await loadRuntimeConfig();

        expect(environment.baseUrl).toBe('https://taurus.example.org/api');
        expect(environment.keycloak.realm).toBe(compiled.keycloak.realm);
        expect(environment.keycloak.clientId).toBe(compiled.keycloak.clientId);
    });

    // Un deploy a una sola origine configura `/auth`: Keycloak concatena quel valore e lo usa nelle
    // redirezioni, quindi deve arrivare assoluto.
    it('resolves a relative Keycloak address on the current origin', async () => {
        serve({ keycloak: { baseurl: '/auth' } });

        await loadRuntimeConfig();

        expect(environment.keycloak.baseurl).toBe(`${window.location.origin}/auth`);
    });

    it('keeps the compiled values when the file is missing', async () => {
        serve(null, false, 404);

        await loadRuntimeConfig();

        expect(environment.baseUrl).toBe(compiled.baseUrl);
        expect(environment.keycloak.realm).toBe(compiled.keycloak.realm);
    });

    it('keeps the compiled values when the file is not readable', async () => {
        spyOn(window, 'fetch').and.rejectWith(new TypeError('rete non disponibile'));

        await loadRuntimeConfig();

        expect(environment.baseUrl).toBe(compiled.baseUrl);
        expect(environment.keycloak.clientId).toBe(compiled.keycloak.clientId);
    });
});
