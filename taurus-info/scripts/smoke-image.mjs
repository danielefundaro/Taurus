/**
 * Smoke test dell'immagine servita da nginx: la costruisce, la avvia e interroga il sito come lo
 * interrogherebbe un browser. I controlli di `check-build.mjs` leggono i file e la configurazione;
 * qui si verifica che il server li serva davvero, con le intestazioni di sicurezza, il punto di
 * salute e la pagina di errore.
 *
 * Si esegue con `pnpm test:image` e richiede Docker. Senza Docker il controllo si salta, a meno di
 * `TAURUS_REQUIRE_DOCKER=1`, come fa la pipeline del backend.
 */
import { execFileSync, spawnSync } from 'node:child_process';
import { createServer } from 'node:net';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('..', import.meta.url));
const image = 'taurus-info:smoke';
const container = `taurus-info-smoke-${process.pid}`;
const requiredHeaders = {
    'x-content-type-options': 'nosniff',
    'x-frame-options': 'DENY',
    'referrer-policy': 'strict-origin-when-cross-origin'
};
const problems = [];

const docker = (args, options = {}) => execFileSync('docker', args, { encoding: 'utf8', ...options });

const dockerAvailable = () => spawnSync('docker', ['version'], { stdio: 'ignore' }).status === 0;

/** Una porta libera sul loopback: il container la pubblica solo lì. */
const freePort = async () =>
    new Promise((resolve, reject) => {
        const probe = createServer();
        probe.unref();
        probe.on('error', reject);
        probe.listen(0, '127.0.0.1', () => {
            const { port } = probe.address();
            probe.close(() => resolve(port));
        });
    });

const waitForHealth = async (base) => {
    const deadline = Date.now() + 60_000;
    while (Date.now() < deadline) {
        try {
            const response = await fetch(`${base}/health`);
            if (response.ok && (await response.text()).trim() === 'ok') return true;
        } catch {
            // Il container sta ancora salendo.
        }
        await new Promise((resolve) => setTimeout(resolve, 500));
    }

    return false;
};

const checkHomePage = async (base) => {
    const response = await fetch(`${base}/`);
    if (response.status !== 200) problems.push(`la home risponde ${response.status} invece di 200`);
    if (!(response.headers.get('content-type') ?? '').includes('text/html')) {
        problems.push(`la home ha il tipo ${response.headers.get('content-type')}`);
    }
    for (const [header, value] of Object.entries(requiredHeaders)) {
        const served = response.headers.get(header);
        if (served !== value) problems.push(`la home serve ${header}=${served} invece di ${value}`);
    }
    if (!response.headers.get('content-security-policy')) problems.push('la home non serve la Content-Security-Policy');
    if (!(await response.text()).includes('<main')) problems.push('la home servita non contiene <main>');
};

const checkNotFound = async (base) => {
    const response = await fetch(`${base}/una-pagina-che-non-esiste/`);
    if (response.status !== 404) problems.push(`una pagina inesistente risponde ${response.status} invece di 404`);
    if (!(await response.text()).toLowerCase().includes('<html')) problems.push('la pagina di errore non è una pagina HTML');
};

/** Nessuna traccia della versione del server nelle risposte. */
const checkServerTokens = async (base) => {
    const response = await fetch(`${base}/health`);
    const server = response.headers.get('server') ?? '';
    if (/\d/.test(server)) problems.push(`l'intestazione Server rivela la versione: ${server}`);
};

if (!dockerAvailable()) {
    if (process.env.TAURUS_REQUIRE_DOCKER === '1') {
        console.error('Docker non disponibile e TAURUS_REQUIRE_DOCKER=1: smoke dell’immagine non eseguito.');
        process.exit(1);
    }
    console.log('Docker non disponibile: smoke dell’immagine saltato.');
    process.exit(0);
}

const port = await freePort();
const base = `http://127.0.0.1:${port}`;
let started = false;
try {
    console.log(`Costruzione di ${image}…`);
    docker(['build', '-t', image, '.'], { cwd: root, stdio: ['ignore', 'inherit', 'inherit'] });
    docker(['run', '-d', '--name', container, '-p', `127.0.0.1:${port}:80`, image]);
    started = true;

    if (!(await waitForHealth(base))) {
        problems.push('il punto di salute /health non ha risposto entro un minuto');
    } else {
        await checkHomePage(base);
        await checkNotFound(base);
        await checkServerTokens(base);
    }
} finally {
    if (started) {
        try {
            console.log(docker(['logs', '--tail', '20', container]));
        } catch {
            // I log sono solo di aiuto alla diagnosi.
        }
        spawnSync('docker', ['rm', '-f', container], { stdio: 'ignore' });
    }
}

if (problems.length > 0) {
    console.error(`Smoke dell’immagine non superato (${problems.length}):`);
    problems.forEach((message) => console.error(`- ${message}`));
    process.exit(1);
}

console.log('Immagine verificata: salute, home con le intestazioni di sicurezza, pagina di errore e server anonimo.');
