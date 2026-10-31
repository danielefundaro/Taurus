/**
 * Controlli sul sito costruito: quello che un browser e un motore di ricerca trovano davvero.
 *
 * Verifica la presenza dei file attesi, i metadati di ogni pagina, i collegamenti interni, la
 * corrispondenza con la sitemap, l'accessibilità strutturale delle pagine e le intestazioni di
 * sicurezza dichiarate in `nginx.conf`.
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

/** L'include che porta le intestazioni di sicurezza dentro un blocco della configurazione. */
const SECURITY_HEADERS_INCLUDE = 'include /etc/nginx/taurus-security-headers.conf;';

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

/**
 * I controlli di accessibilità che si possono fare sul file costruito, senza browser: struttura dei
 * titoli, testo alternativo, nome accessibile di collegamenti e pulsanti, etichette dei campi e
 * punti di riferimento della pagina. Il contrasto e il comportamento della tastiera restano al
 * gate axe della suite end to end del frontend, che gira su un browser vero.
 */
const tags = (html, name) => [...html.matchAll(new RegExp(`<${name}\b([^>]*)>`, 'gi'))].map((match) => match[1]);

const attribute = (attributes, name) => {
    const found = new RegExp(`\b${name}\s*=\s*"([^"]*)"`, 'i').exec(attributes);

    return found ? found[1] : null;
};

/** Il testo dentro un elemento, senza marcatori: serve a dire se un collegamento ha un nome. */
const textOf = (html, name) => [...html.matchAll(new RegExp(`<${name}\b([^>]*)>([\s\S]*?)</${name}>`, 'gi'))]
    .map((match) => [match[1], match[2].replace(/<[^>]*>/g, '').replace(/&[a-z]+;/gi, ' ').trim()]);

const checkAccessibility = (file, html) => {
    const route = routeOf(file);
    const headings = [...html.matchAll(/<h([1-6])\b/gi)].map((match) => Number(match[1]));
    const firstLevel = headings.filter((level) => level === 1).length;
    if (firstLevel !== 1) problem(`${route}: la pagina ha ${firstLevel} titoli di primo livello invece di uno`);
    headings.reduce((previous, level) => {
        if (level > previous + 1) problem(`${route}: il titolo h${level} salta un livello dopo h${previous}`);

        return level;
    }, headings[0] ?? 1);

    if (!/<main\b/i.test(html)) problem(`${route}: manca il punto di riferimento <main>`);

    for (const attributes of tags(html, 'img')) {
        if (attribute(attributes, 'alt') === null) problem(`${route}: un'immagine non ha il testo alternativo`);
    }

    for (const [attributes, text] of textOf(html, 'a')) {
        const named = text.length > 0 || attribute(attributes, 'aria-label') || attribute(attributes, 'title');
        if (!named) problem(`${route}: un collegamento non ha un nome accessibile`);
    }

    for (const [attributes, text] of textOf(html, 'button')) {
        if (text.length === 0 && !attribute(attributes, 'aria-label')) problem(`${route}: un pulsante non ha un nome accessibile`);
    }

    const labelled = new Set(
        tags(html, 'label')
            .map((attributes) => attribute(attributes, 'for'))
            .filter(Boolean)
    );
    for (const name of ['input', 'select', 'textarea']) {
        for (const attributes of tags(html, name)) {
            const type = (attribute(attributes, 'type') ?? '').toLowerCase();
            if (name === 'input' && ['hidden', 'submit', 'button', 'reset', 'image'].includes(type)) continue;
            const id = attribute(attributes, 'id');
            const named = (id && labelled.has(id)) || attribute(attributes, 'aria-label') || attribute(attributes, 'aria-labelledby');
            if (!named) problem(`${route}: un campo ${name} non ha un'etichetta`);
        }
    }

    const positiveTabindex = [...html.matchAll(/tabindex\s*=\s*"([0-9]+)"/gi)].some((match) => Number(match[1]) > 0);
    if (positiveTabindex) problem(`${route}: un tabindex positivo cambia l'ordine di tabulazione`);
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
    const headers = await readFile(join(root, 'security-headers.conf'), 'utf8');
    for (const header of requiredHeaders) {
        if (!headers.includes(header)) problem(`security-headers.conf: manca l'intestazione ${header}`);
    }
    if (!/location\s*=\s*\/health/.test(config)) problem('nginx.conf: manca il punto di salute /health');

    // nginx non eredita gli add_header del blocco server in un location che ne dichiara di propri:
    // ogni location con un add_header suo deve includere di nuovo le intestazioni di sicurezza.
    const blocks = [...config.matchAll(/location([^{]*)\{([^}]*)\}/g)];
    for (const [, head, body] of blocks) {
        if (!body.includes('add_header')) continue;
        if (!body.includes(SECURITY_HEADERS_INCLUDE)) {
            problem(`nginx.conf: il blocco location${head.trim()} dichiara add_header senza includere le intestazioni di sicurezza`);
        }
    }
    if (!config.includes(SECURITY_HEADERS_INCLUDE)) problem('nginx.conf: non include security-headers.conf');
};

if (!(await exists(dist))) {
    console.error('dist non esiste: eseguire prima `pnpm build`.');
    process.exit(1);
}

await checkRequiredFiles();
const files = await htmlFiles(dist);
const pages = await Promise.all(files.map(async (file) => [file, await readFile(file, 'utf8')]));
pages.forEach(([file, html]) => checkPage(file, html));
pages.forEach(([file, html]) => checkAccessibility(file, html));
await checkInternalLinks(pages);
await checkSitemap(pages);
await checkServerHeaders();

if (problems.length > 0) {
    console.error(`Controlli del sito non superati (${problems.length}):`);
    problems.forEach((message) => console.error(`- ${message}`));
    process.exit(1);
}

console.log(
    `Sito verificato: ${pages.length} pagine, accessibilità strutturale, collegamenti interni, sitemap e intestazioni del server.`
);
