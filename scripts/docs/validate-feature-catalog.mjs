#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import {
    CATALOG_PATH,
    defaultRepositoryRoot,
    loadCatalog,
    markdownText,
    validateCatalog,
    validateReleaseTag,
} from './feature-catalog-lib.mjs';

const args = process.argv.slice(2);
const releaseIndex = args.indexOf('--release-tag');
const releaseTag = releaseIndex >= 0 ? args[releaseIndex + 1] : null;
let catalog;

try {
    catalog = loadCatalog(defaultRepositoryRoot);
} catch (error) {
    console.error(`${CATALOG_PATH}: invalid JSON: ${error.message}`);
    process.exitCode = 1;
}

if (catalog) {
    const errors = validateCatalog(catalog, defaultRepositoryRoot);
    if (releaseIndex >= 0 && !releaseTag) errors.push('--release-tag: requires a value');
    if (releaseTag && !validateReleaseTag(releaseTag)) errors.push(`--release-tag: ${JSON.stringify(releaseTag)} must match vMAJOR.MINOR.PATCH`);

    if (errors.length > 0) {
        for (const error of errors.slice(0, 100)) console.error(`${CATALOG_PATH}: ${error}`);
        if (errors.length > 100) console.error(`${CATALOG_PATH}: ${errors.length - 100} more errors omitted`);
        process.exitCode = 1;
    } else {
        console.log(`${CATALOG_PATH}: valid (${catalog.entries.length} entries)`);
        if (releaseTag) {
            const released = catalog.entries.filter(entry => entry.release?.tag === releaseTag);
            console.log(`Release ${releaseTag}: ${released.length} first-release feature(s)`);
            for (const entry of released) console.log(`- ${entry.id}: ${markdownText(entry.title)}`);

            const summaryPath = process.env.GITHUB_STEP_SUMMARY;
            if (summaryPath) {
                const lines = [`## Catalogo release ${releaseTag}`, '', released.length === 0 ? 'Nessuna funzionalità indica questa release come prima release.' : 'Funzionalità alla prima release:', ''];
                for (const entry of released) lines.push(`- \`${entry.id}\`: ${markdownText(entry.title)}`);
                fs.appendFileSync(path.resolve(summaryPath), `${lines.join('\n')}\n`, 'utf8');
            }
        }
    }
}
