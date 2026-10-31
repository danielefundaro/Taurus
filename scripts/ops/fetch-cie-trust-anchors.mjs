#!/usr/bin/env node
// Procura i certificati delle CA della Carta d'Identita' Elettronica e ne scrive il bundle che il
// backend usa come trust anchor di CieSign (`SIGNATURE_CIE_TRUST_ANCHORS`).
//
// La fonte e' la lista di fiducia italiana pubblicata da AgID: il documento che dice quali CA sono
// riconosciute, e che e' la stessa fonte indicata dal punto 4.7 del runbook operativo. Da lei si
// prendono i certificati del prestatore «Ministero dell'Interno», cioe' la CA radice della CIE e le
// issuing sub CA che emettono i certificati delle carte.
//
// Lo scaricamento e' automatico, la fiducia no: le impronte attese stanno in
// `scripts/ops/cie-trust-anchors.sha256`, versionate, e un certificato che non e' in quell'elenco
// fa fallire lo script invece di finire nel bundle. E' lo stesso schema del SoundFont del lettore
// (`taurus-fe/scripts/fetch-soundfont.mjs`) e dei modelli del servizio OMR (`taurus-omr/
// models.sha256`): la rete porta i byte, il repository dice quali byte sono accettabili. Il rinnovo
// di una CA quindi non entra di nascosto: arriva come modifica da rivedere (`--update-pins`).
//
// Il bundle e' versionato in `deploy/cie/trust-anchors.pem` e il manifest di produzione lo monta in
// sola lettura: un'installazione parte con CieSign pronto, senza chiamare AgID all'avvio.
//
//   node scripts/ops/fetch-cie-trust-anchors.mjs                 scarica, verifica e riscrive il bundle
//   node scripts/ops/fetch-cie-trust-anchors.mjs --check         scarica e confronta, senza scrivere (CI)
//   node scripts/ops/fetch-cie-trust-anchors.mjs --verify-bundle confronta bundle e impronte, senza rete
//   node scripts/ops/fetch-cie-trust-anchors.mjs --update-pins   riscrive anche le impronte attese
//   node scripts/ops/fetch-cie-trust-anchors.mjs --tsl <file>    legge una copia locale della lista
//
// Esce 0 se tutto torna, 1 elencando quello che non torna, 2 per un uso sbagliato.
import { createHash, X509Certificate } from 'node:crypto';
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const TSL_URL = process.env.TAURUS_CIE_TSL_URL || 'https://eidas.agid.gov.it/TL/TSL-IT.xml';
// Il prestatore della CIE nella lista di fiducia, e il marcatore che devono avere i soggetti dei
// suoi certificati: il Ministero dell'Interno pubblica anche altri servizi.
const PROVIDER = "Ministero dell'Interno";
const SUBJECT_MARK = 'Italian Electronic Identity Card';
// Gli stati che valgono come «in vigore»: un servizio ritirato o sospeso non e' piu' una CA di cui
// fidarsi, e va fuori dal bundle anche se la lista continua a elencarlo.
const ACTIVE = new Set(['granted', 'recognisedatnationallevel']);
const EXPIRY_WARNING_DAYS = 60;

const root = join(dirname(fileURLToPath(import.meta.url)), '..', '..');
const BUNDLE = join(root, 'deploy', 'cie', 'trust-anchors.pem');
const PINS = join(root, 'scripts', 'ops', 'cie-trust-anchors.sha256');

const argv = process.argv.slice(2);
const mode = argv.includes('--check')
    ? 'check'
    : argv.includes('--verify-bundle')
      ? 'verify-bundle'
      : argv.includes('--update-pins')
        ? 'update-pins'
        : 'write';
const tslFile = argv.includes('--tsl') ? argv[argv.indexOf('--tsl') + 1] : process.env.TAURUS_CIE_TSL_PATH;
if (argv.includes('--tsl') && !tslFile) {
    console.error('uso: node scripts/ops/fetch-cie-trust-anchors.mjs --tsl <file>');
    process.exit(2);
}

let problems = 0;
const fail = (message) => {
    console.error(`  NO  ${message}`);
    problems += 1;
};
const ok = (message) => console.log(`  ok  ${message}`);

// --- Lettura della lista di fiducia -------------------------------------------------------------
// La lista e' un XML di qualche megabyte di cui serve un pezzo solo. Si naviga per elementi con i
// tagli qui sotto invece di montare un parser: non si interpretano attributi, namespace o entita',
// si isolano blocchi e si legge il base64 dei certificati, che poi vale solo se la sua impronta e'
// tra quelle attese. Qualunque cambio di forma del documento fa trovare zero certificati, e questo
// e' un errore, non un bundle vuoto.

/** I blocchi `<tag>...</tag>` contenuti nel testo, senza i tag. */
function blocks(text, tag) {
    const open = `<${tag}>`;
    const close = `</${tag}>`;
    const found = [];
    let from = 0;
    for (;;) {
        const start = text.indexOf(open, from);
        if (start < 0) return found;
        const end = text.indexOf(close, start);
        if (end < 0) return found;
        found.push(text.slice(start + open.length, end));
        from = end + close.length;
    }
}

/** Il contenuto del primo `<tag>` del testo, oppure la stringa vuota. */
function first(text, tag) {
    return blocks(text, tag)[0] ?? '';
}

function readTsl(xml) {
    const provider = blocks(xml, 'TrustServiceProvider').find((block) => first(block, 'TSPInformation').includes(PROVIDER));
    if (!provider) throw new Error(`la lista di fiducia non contiene il prestatore «${PROVIDER}»`);

    const found = new Map();
    for (const service of blocks(first(provider, 'TSPServices'), 'TSPService')) {
        // Solo le informazioni correnti del servizio: `ServiceHistory` elenca gli stati passati,
        // e con essi certificati che oggi non sono piu' in vigore.
        const information = first(service, 'ServiceInformation');
        const status = first(information, 'ServiceStatus').trim().split('/').pop();
        const type = first(information, 'ServiceTypeIdentifier').trim().split('/').pop();
        const name = first(first(information, 'ServiceName'), 'Name').trim();
        if (!ACTIVE.has(status)) {
            console.log(`  --  servizio non in vigore, ignorato (${status}): ${name}`);
            continue;
        }
        for (const identity of blocks(information, 'ServiceDigitalIdentity')) {
            for (const encoded of blocks(identity, 'X509Certificate')) {
                const der = Buffer.from(encoded.replace(/\s+/g, ''), 'base64');
                let certificate;
                try {
                    certificate = new X509Certificate(der);
                } catch (error) {
                    fail(`certificato illeggibile nel servizio «${name}»: ${error.message}`);
                    continue;
                }
                if (!certificate.subject.includes(SUBJECT_MARK)) continue;
                const entry = found.get(digest(der)) ?? { der, certificate, services: new Set() };
                entry.services.add(type);
                found.set(digest(der), entry);
            }
        }
    }
    if (found.size === 0) throw new Error('nessun certificato della CIE trovato: la lista di fiducia ha cambiato forma');
    return [...found.entries()].map(([sha256, entry]) => ({ sha256, ...entry }));
}

// L'impronta del certificato nella forma di `sha256sum`, che e' quella con cui si confronta a mano:
// e' l'impronta del DER, la stessa che stampano `openssl x509 -fingerprint -sha256` e `keytool`.
const digest = (der) => createHash('sha256').update(der).digest('hex');

const date = (value, what) => {
    const parsed = value instanceof Date ? value : new Date(value);
    if (Number.isNaN(parsed.getTime())) throw new Error(`${what} illeggibile: ${value}`);
    return parsed;
};
const notAfter = (certificate) => date(certificate.validToDate ?? certificate.validTo, 'scadenza');
const notBefore = (certificate) => date(certificate.validFromDate ?? certificate.validFrom, "inizio di validita'");

// Node espone il soggetto una riga per attributo, quindi il nome si prende fino a fine riga.
const commonName = (certificate) => /^CN=(.*)$/m.exec(certificate.subject)?.[1]?.trim() ?? certificate.subject.replace(/\s+/g, ' ');
const day = (date) => date.toISOString().slice(0, 10);

// --- Verifiche --------------------------------------------------------------------------------

/** Radici e sub CA in un ordine stabile: le radici prima, poi per nome e inizio di validita'. */
function sorted(certificates) {
    return [...certificates].sort((left, right) => {
        if (left.isRoot !== right.isRoot) return left.isRoot ? -1 : 1;
        const byName = commonName(left.certificate).localeCompare(commonName(right.certificate));
        return byName !== 0 ? byName : notBefore(left.certificate) - notBefore(right.certificate);
    });
}

/** Classifica i certificati e controlla che ognuno sia una radice o sia emesso da una di esse. */
function qualify(certificates) {
    for (const entry of certificates) {
        const certificate = entry.certificate;
        entry.isRoot = certificate.subject === certificate.issuer && certificate.verify(certificate.publicKey);
    }
    const roots = certificates.filter((entry) => entry.isRoot);
    if (roots.length === 0) fail('nessuna CA radice tra i certificati: il bundle non avrebbe un punto di partenza');
    for (const entry of certificates.filter((candidate) => !candidate.isRoot)) {
        // Una sub CA che non risale a una radice dell'insieme non e' una CA della CIE: e' un
        // certificato arrivato da qualche altra parte, e non deve diventare attendibile.
        if (!roots.some((candidate) => entry.certificate.checkIssued(candidate.certificate))) {
            fail(`«${commonName(entry.certificate)}» non e' emesso da nessuna delle CA radice trovate`);
        }
    }
    return sorted(certificates);
}

function reportValidity(certificates, now) {
    for (const entry of certificates) {
        const expiry = notAfter(entry.certificate);
        const days = Math.floor((expiry.getTime() - now.getTime()) / 86400000);
        const what = `${entry.isRoot ? 'CA radice' : 'sub CA'} «${commonName(entry.certificate)}» scade il ${day(expiry)}`;
        if (days < 0) {
            fail(`${what}: e' scaduta, e le firme fatte dopo quella data sono rifiutate`);
        } else if (days <= EXPIRY_WARNING_DAYS) {
            console.log(`  !!  ${what}: mancano ${days} giorni, va rinnovata`);
        } else {
            ok(what);
        }
    }
}

// --- Impronte attese e bundle -----------------------------------------------------------------

function readPins() {
    // Senza l'elenco non c'e' niente di cui fidarsi, e lo script si ferma; solo `--update-pins` puo'
    // partire da zero, perche' e' il comando con cui l'elenco si scrive la prima volta.
    if (!existsSync(PINS)) {
        if (mode === 'update-pins') return new Map();
        throw new Error(`impronte attese mancanti: ${PINS}`);
    }
    const pins = new Map();
    for (const line of readFileSync(PINS, 'utf8').split(/\r?\n/)) {
        const trimmed = line.trim();
        if (trimmed === '' || trimmed.startsWith('#')) continue;
        const [sha256, ...label] = trimmed.split(/\s+/);
        if (!/^[0-9a-f]{64}$/.test(sha256)) throw new Error(`impronta non valida in ${PINS}: ${trimmed}`);
        pins.set(sha256, label.join(' '));
    }
    if (pins.size === 0 && mode !== 'update-pins') throw new Error(`nessuna impronta attesa in ${PINS}`);
    return pins;
}

function comparePins(certificates, pins) {
    const extra = certificates.filter((entry) => !pins.has(entry.sha256));
    const missing = [...pins.keys()].filter((sha256) => !certificates.some((entry) => entry.sha256 === sha256));
    for (const entry of extra) {
        fail(`certificato non atteso: ${entry.sha256}  ${commonName(entry.certificate)} (scade il ${day(notAfter(entry.certificate))})`);
    }
    for (const sha256 of missing) {
        fail(`impronta attesa che la fonte non contiene piu': ${sha256}  ${pins.get(sha256)}`);
    }
    if (extra.length === 0 && missing.length === 0) ok(`le ${certificates.length} impronte corrispondono a quelle attese`);
    else {
        console.error(
            '\nUna CA della CIE e\' cambiata. Verificare i certificati sulla fonte ufficiale, poi registrare la\n' +
                'modifica con `node scripts/ops/fetch-cie-trust-anchors.mjs --update-pins` e farla rivedere:\n' +
                'il bundle e le impronte vanno in un commit, non in un aggiornamento silenzioso.'
        );
    }
}

const pem = (der) => {
    const body = der.toString('base64').replace(/(.{64})/g, '$1\n').replace(/\n$/, '');
    return `-----BEGIN CERTIFICATE-----\n${body}\n-----END CERTIFICATE-----\n`;
};

/**
 * Il bundle: un commento per ogni certificato e il suo PEM. Java legge l'insieme con una
 * `CertificateFactory` sola e ignora il testo fuori dai blocchi, quindi il file resta leggibile da
 * chi lo apre senza smettere di essere caricabile dal backend.
 */
function renderBundle(certificates) {
    const header =
        '# Certificati delle CA della Carta d\'Identita\' Elettronica, trust anchor di CieSign.\n' +
        '# Generato da `scripts/ops/fetch-cie-trust-anchors.mjs` dalla lista di fiducia italiana di AgID.\n' +
        '# Non modificare a mano: le impronte attese stanno in `scripts/ops/cie-trust-anchors.sha256`.\n';
    const body = certificates.map(
        (entry) =>
            `\n# ${entry.isRoot ? 'CA radice' : 'sub CA'}: ${commonName(entry.certificate)}\n` +
            `# valido dal ${day(notBefore(entry.certificate))} al ${day(notAfter(entry.certificate))}\n` +
            `# sha256 ${entry.sha256}\n` +
            pem(entry.der)
    );
    return header + body.join('');
}

function renderPins(certificates) {
    return (
        '# Impronte SHA-256 dei certificati delle CA della Carta d\'Identita\' Elettronica accettati come\n' +
        '# trust anchor di CieSign: sono la decisione di fiducia, e stanno nel repository perche\' una CA\n' +
        '# non diventi attendibile solo per essere arrivata dalla rete.\n' +
        '#\n' +
        '# Si aggiornano con `node scripts/ops/fetch-cie-trust-anchors.mjs --update-pins`, dopo aver\n' +
        '# verificato i certificati sulla fonte ufficiale (punto 4.7 del runbook operativo). Il formato\n' +
        "# e' quello di `sha256sum`, ma la seconda colonna e' il nome del certificato, non un file.\n" +
        certificates.map((entry) => `${entry.sha256}  ${commonName(entry.certificate)} (scade il ${day(notAfter(entry.certificate))})\n`).join('')
    );
}

function readBundle() {
    if (!existsSync(BUNDLE)) throw new Error(`bundle mancante: ${BUNDLE}. Generarlo con \`node scripts/ops/fetch-cie-trust-anchors.mjs\``);
    const content = readFileSync(BUNDLE, 'utf8');
    const certificates = [];
    for (const block of content.matchAll(/-----BEGIN CERTIFICATE-----([\s\S]*?)-----END CERTIFICATE-----/g)) {
        const der = Buffer.from(block[1].replace(/\s+/g, ''), 'base64');
        certificates.push({ sha256: digest(der), der, certificate: new X509Certificate(der), services: new Set() });
    }
    if (certificates.length === 0) throw new Error(`nessun certificato in ${BUNDLE}`);
    return certificates;
}

// --- Esecuzione -------------------------------------------------------------------------------

async function source() {
    if (tslFile) {
        console.log(`Lista di fiducia: copia locale ${tslFile}`);
        return readFileSync(tslFile, 'utf8');
    }
    console.log(`Lista di fiducia: ${TSL_URL}`);
    const response = await fetch(TSL_URL);
    if (!response.ok) throw new Error(`la lista di fiducia non risponde: HTTP ${response.status}`);
    return await response.text();
}

const now = new Date();
try {
    let certificates;
    if (mode === 'verify-bundle') {
        console.log(`== Bundle ${BUNDLE}`);
        certificates = qualify(readBundle());
        ok(`${certificates.length} certificati leggibili nel bundle`);
    } else {
        console.log('== Lista di fiducia');
        certificates = qualify(readTsl(await source()));
        ok(`${certificates.length} certificati della CIE in vigore`);
    }

    console.log('\n== Validita\'');
    reportValidity(certificates, now);

    console.log('\n== Impronte attese');
    const pins = readPins();
    if (mode === 'update-pins') {
        // Le impronte si riscrivono solo quando sono state chieste: e' la modifica da rivedere.
        for (const entry of certificates) {
            if (!pins.has(entry.sha256)) console.log(`  +   ${entry.sha256}  ${commonName(entry.certificate)}`);
        }
        for (const [sha256, label] of pins) {
            if (!certificates.some((entry) => entry.sha256 === sha256)) console.log(`  -   ${sha256}  ${label}`);
        }
    } else {
        comparePins(certificates, pins);
    }

    if (problems === 0 && mode !== 'verify-bundle') {
        const bundle = renderBundle(certificates);
        if (mode === 'check') {
            console.log('\n== Bundle versionato');
            if (existsSync(BUNDLE) && readFileSync(BUNDLE, 'utf8').replace(/\r\n/g, '\n') === bundle) {
                ok(`${BUNDLE} e' aggiornato`);
            } else {
                fail(`${BUNDLE} non corrisponde alla fonte: rigenerarlo con \`node scripts/ops/fetch-cie-trust-anchors.mjs\``);
            }
        } else {
            mkdirSync(dirname(BUNDLE), { recursive: true });
            writeFileSync(BUNDLE, bundle);
            console.log(`\nBundle scritto: ${BUNDLE}`);
            if (mode === 'update-pins') {
                writeFileSync(PINS, renderPins(certificates));
                console.log(`Impronte attese riscritte: ${PINS}. Rivedere il diff prima di committarlo.`);
            }
        }
    }
} catch (error) {
    console.error(`Certificati della CIE non procurati: ${error.message}`);
    process.exit(1);
}

console.log();
if (problems > 0) {
    console.log(`Certificati della CIE non in regola. Controlli falliti: ${problems}.`);
    process.exit(1);
}
console.log("Certificati della CIE in regola: fonte, validita', emittente e impronte attese.");
