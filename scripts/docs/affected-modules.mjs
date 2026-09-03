#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import { defaultRepositoryRoot, loadCatalog } from './feature-catalog-lib.mjs';

const input = process.argv[2];
if (!input) {
    console.error('usage: node scripts/docs/affected-modules.mjs <changed-files>');
    process.exit(1);
}
const changed = fs
    .readFileSync(path.resolve(defaultRepositoryRoot, input), 'utf8')
    .split(/\r?\n/)
    .map(value => value.trim().replaceAll('\\', '/'))
    .filter(Boolean);
const affected = new Set();
const catalog = loadCatalog(defaultRepositoryRoot);

for (const repositoryPath of changed) {
    for (const module of ['taurus-be', 'taurus-fe', 'keycloak-authenticator', 'taurus-info']) {
        if (repositoryPath.startsWith(`${module}/`)) affected.add(module);
    }
    for (const entry of catalog.entries) {
        if (repositoryPath === entry.spec || entry.relatedDocs?.includes(repositoryPath)) {
            entry.modules.filter(module => module !== 'repository').forEach(module => affected.add(module));
        }
    }
}

for (const [output, module] of [
    ['backend', 'taurus-be'],
    ['frontend', 'taurus-fe'],
    ['keycloak', 'keycloak-authenticator'],
    ['info_site', 'taurus-info'],
]) {
    console.log(`${output}=${affected.has(module)}`);
}
