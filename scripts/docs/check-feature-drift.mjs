#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import { defaultRepositoryRoot, loadCatalog } from './feature-catalog-lib.mjs';

const args = process.argv.slice(2);
const changedIndex = args.indexOf('--changed-files');
const changedPath = changedIndex >= 0 ? args[changedIndex + 1] : null;
if (!changedPath) {
    console.error('usage: node scripts/docs/check-feature-drift.mjs --changed-files <file>');
    process.exit(1);
}

const changed = new Set(
    fs
        .readFileSync(path.resolve(defaultRepositoryRoot, changedPath), 'utf8')
        .split(/\r?\n/)
        .map(value => value.trim().replaceAll('\\', '/'))
        .filter(Boolean),
);
const catalog = loadCatalog(defaultRepositoryRoot);
const catalogChanged = changed.has('docs/features.json');
const warnings = [];

for (const entry of catalog.entries) {
    const evidence = Object.values(entry.evidence).flat();
    const touched = evidence.filter(value => changed.has(value));
    if (touched.length > 0 && !catalogChanged) {
        warnings.push(`La voce \`${entry.id}\` non è stata verificata nel catalogo, ma sono cambiate evidenze registrate: ${touched.join(', ')}.`);
    }
    if (changed.has(entry.spec) && !catalogChanged) {
        warnings.push(`La specifica di \`${entry.id}\` è cambiata senza aggiornare \`docs/features.json\`.`);
    }
}

const moduleCodeChanged = [...changed].some(value => /^(taurus-be|taurus-fe|keycloak-authenticator|taurus-info)\//.test(value));
const knownEvidenceChanged = catalog.entries.some(entry => Object.values(entry.evidence).flat().some(value => changed.has(value)));
if (moduleCodeChanged && !knownEvidenceChanged && !catalogChanged) {
    warnings.push('Sono cambiati file applicativi senza toccare evidenze registrate o il catalogo: verificare se la pull request richiede un Feature ID.');
}

if (warnings.length === 0) {
    console.log('Nessun possibile disallineamento documentale rilevato.');
} else {
    for (const warning of warnings) console.log(`::warning title=Possibile deriva documentale::${warning}`);
    if (process.env.GITHUB_STEP_SUMMARY) {
        fs.appendFileSync(
            process.env.GITHUB_STEP_SUMMARY,
            `## Possibile deriva documentale\n\n${warnings.map(value => `- ${value}`).join('\n')}\n`,
            'utf8',
        );
    }
}
