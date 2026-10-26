/**
 * Controlli sul sito costruito: quello che un browser e un motore di ricerca trovano davvero.
 *
 * Verifica la presenza dei file attesi, i metadati di ogni pagina, i collegamenti interni, la
 * corrispondenza con la sitemap e le intestazioni di sicurezza dichiarate in `nginx.conf`.
 * Si esegue dopo `astro build`, con `pnpm test`.
 */
import { readdir, readFile, stat } from 'node:fs/promises';
import { join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('..', import.meta.url));
const dist = join(root, 'dist');
const problems = [];

const required = ['index.html', '404.html', 'robots.txt', 'sitemap-index.xml', 'favicon.ico', 'site.webmanifest'];

/** Le intestazioni che la configurazione del server deve dichiarare per ogni risposta. */
const requiredHeaders = [
    'X-Content-Type-Options',
    'X-Frame-Options',
    'Referrer-Policy',
    'Permissions-Policy',
    'Content-Security-Policy'
];

const problem = (message) => problems.push(message);

const exists = async (path) => {
    try {
        await stat(path);
        return true;
    } catch {
        return false;
    }
};

const htmlFiles = async (directory) => {
    const entries = await readdir(directory, { withFileTypes: true });
    const found = [];
    for (const entry of entries) {
        const path = join(directory, entry.name);
        if (entry.isDirectory()) found.push(...(await htmlFiles(path)));
        else if (entry.name.endsWith('.html')) found.push(path);
    }

    return found;
};

/** L'indirizzo pubblico di una pagina: `dist/privacy/index.html` è `/privacy/`. */
const routeOf = (file) => {
    const path = relative(dist, file).split('\\').join('/');
    if (path === 'index.html') return '/';
    if (path.endsWith('/index.html')) return `/${path.slice(0, -'index.html'.length)}`;

    return `/${path}`;
};

const checkRequiredFiles = async () => {
    for (const name of required) {
        if (!(await exists(join(dist, name)))) problem(`manca ${name} in dist`);
    }
};

const checkPage = (file, html) => {
    const route = routeOf(file);
    if (!/<html[^>]+lang="it"/.test(html)) problem(`${route}: manca lang="it" su <html>`);
    if (!/<title>[^<]{3,}<\/title>/.test(html)) problem(`${route}: manca il titolo`);
    if (!/<meta\s+name="description"\s+content="[^"]{10,}"/.test(html)) problem(`${route}: manca la descrizione`);
    if (route !== '/404.html' && !/<link\s+rel="canonical"/.test(html)) problem(`${route}: manca il canonical`);
    if (!/<meta\s+property="og:image"/.test(html)) problem(`${route}: manca og:image`);
};

const checkInternalLinks = async (pages) => {
    const routes = new Set(pages.map(([file]) => routeOf(file)));
    for (const [file, html] of pages) {
        const links = [...html.matchAll(/href="(\/[^"#?]*)/g)].map((match) => match[1]);
        for (const link of new Set(links)) {
            const asRoute = link.endsWith('/') ? link : `${link}/`;
            if (routes.has(link) || routes.has(asRoute)) continue;
            if (await exists(join(dist, link))) continue;
            problem(`${routeOf(file)}: il collegamento ${link} non esiste in dist`);
        }
    }
};

const checkSitemap = async (pages) => {
    const sitemaps = (await readdir(dist)).filter((name) => name.startsWith('sitemap') && name.endsWith('.xml'));
    const listed = new Set();
    for (const name of sitemaps) {
        const xml = await readFile(join(dist, name), 'utf8');
        for (const match of xml.matchAll(/<loc>([^<]+)<\/loc>/g)) {
            const url = match[1];
            if (url.endsWith('.xml')) continue;
            listed.add(new URL(url).pathname);
        }
    }

    for (const [file] of pages) {
        const route = routeOf(file);
        if (route === '/404.html') continue;
        const alternative = route.endsWith('/') ? route.slice(0, -1) : `${route}/`;
        if (!listed.has(route) && !listed.has(alternative)) problem(`${route}: non compare nella sitemap`);
    }
};

const checkServerHeaders = async () => {
    const config = await readFile(join(root, 'nginx.conf'), 'utf8');
    for (const header of requiredHeaders) {
        if (!config.includes(header)) problem(`nginx.conf: manca l'intestazione ${header}`);
    }
    if (!/location\s*=\s*\/health/.test(config)) problem('nginx.conf: manca il punto di salute /health');
};

if (!(await exists(dist))) {
    console.error('dist non esiste: eseguire prima `pnpm build`.');
    process.exit(1);
}

await checkRequiredFiles();
const files = await htmlFiles(dist);
const pages = await Promise.all(files.map(async (file) => [file, await readFile(file, 'utf8')]));
pages.forEach(([file, html]) => checkPage(file, html));
await checkInternalLinks(pages);
await checkSitemap(pages);
await checkServerHeaders();

if (problems.length > 0) {
    console.error(`Controlli del sito non superati (${problems.length}):`);
    problems.forEach((message) => console.error(`- ${message}`));
    process.exit(1);
}

console.log(`Sito verificato: ${pages.length} pagine, collegamenti interni, sitemap e intestazioni del server.`);
