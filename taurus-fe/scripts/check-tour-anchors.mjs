/**
 * Confronta le ancore citate dai cataloghi della guida, quello introduttivo e quello delle guide di pagina, con quelle
 * dichiarate nell'interfaccia e fallisce se una manca: protegge dalla deriva più probabile, una pagina rifatta che
 * perde l'ancora della guida.
 *
 * Le ancore si dichiarano nei template con `appTourAnchor="id"` o con un'espressione come
 * `[appTourAnchor]="vista ? 'a' : 'b'"`, di cui contano tutti i letterali, e nel modello del menu con
 * `tourAnchor: 'id'`. Node 22, senza dipendenze.
 */
import { readdirSync, readFileSync } from 'node:fs';
import { join, relative } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const root = fileURLToPath(new URL('..', import.meta.url));
const catalogPaths = ['guided-tour-steps.ts', 'guided-tour-pages.ts'].map((name) => join(root, 'src/app/constants', name));
const appPath = join(root, 'src/app');

/** Ancore citate dal catalogo, in `anchors` e `mobileAnchors`. */
export function catalogAnchors(source) {
    const anchors = new Set();
    for (const list of source.matchAll(/\b(?:mobileAnchors|anchors)\s*:\s*\[([^\]]*)\]/g)) {
        for (const literal of list[1].matchAll(/['"]([^'"]+)['"]/g)) anchors.add(literal[1]);
    }
    return anchors;
}

/** Ancore dichiarate da un template HTML o da un sorgente TypeScript. */
export function declaredAnchors(source, fileName) {
    if (!fileName.endsWith('.html')) return new Set(Array.from(source.matchAll(/\btourAnchor\s*:\s*['"]([^'"]+)['"]/g), (match) => match[1]));
    const anchors = new Set();
    for (const match of source.matchAll(/(\[?)appTourAnchor\]?\s*=\s*"([^"]*)"/g)) {
        if (!match[1]) anchors.add(match[2]);
        else for (const literal of match[2].matchAll(/'([^']+)'/g)) anchors.add(literal[1]);
    }
    return anchors;
}

export function missingAnchors(cited, declared) {
    return [...cited].filter((anchor) => !declared.has(anchor)).sort();
}

function* sources(directory) {
    for (const entry of readdirSync(directory, { withFileTypes: true })) {
        const path = join(directory, entry.name);
        if (entry.isDirectory()) yield* sources(path);
        else if ((entry.name.endsWith('.html') || entry.name.endsWith('.ts')) && !entry.name.endsWith('.spec.ts') && !catalogPaths.includes(path)) yield path;
    }
}

function main() {
    const cited = new Set(catalogPaths.flatMap((path) => [...catalogAnchors(readFileSync(path, 'utf8'))]));
    const declared = new Map();
    for (const path of sources(appPath)) {
        for (const anchor of declaredAnchors(readFileSync(path, 'utf8'), path)) {
            declared.set(anchor, [...(declared.get(anchor) ?? []), relative(root, path)]);
        }
    }

    const missing = missingAnchors(cited, new Set(declared.keys()));
    if (missing.length) {
        console.error(`Ancore della guida citate dal catalogo ma assenti dall'interfaccia: ${missing.join(', ')}.`);
        console.error('Aggiungi appTourAnchor all\'elemento della pagina, o tourAnchor alla voce del menu, oppure aggiorna il catalogo.');
        process.exit(1);
    }

    const unused = [...declared.keys()].filter((anchor) => !cited.has(anchor)).sort();
    if (unused.length) console.log(`Ancore dichiarate ma non usate dal catalogo: ${unused.join(', ')}.`);
    console.log(`Ancore della guida coerenti: ${cited.size} citate, tutte dichiarate.`);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) main();
