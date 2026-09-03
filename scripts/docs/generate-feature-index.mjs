#!/usr/bin/env node
import fs from 'node:fs';
import path from 'node:path';
import {
    CATALOG_PATH,
    INDEX_PATH,
    defaultRepositoryRoot,
    generateFeatureIndex,
    loadCatalog,
    validateCatalog,
} from './feature-catalog-lib.mjs';

let catalog;
try {
    catalog = loadCatalog(defaultRepositoryRoot);
} catch (error) {
    console.error(`${CATALOG_PATH}: invalid JSON: ${error.message}`);
    process.exit(1);
}

const errors = validateCatalog(catalog, defaultRepositoryRoot);
if (errors.length > 0) {
    for (const error of errors.slice(0, 100)) console.error(`${CATALOG_PATH}: ${error}`);
    process.exit(1);
}

const output = generateFeatureIndex(catalog);
const outputPath = path.resolve(defaultRepositoryRoot, ...INDEX_PATH.split('/'));
const temporaryPath = `${outputPath}.${process.pid}.tmp`;
try {
    fs.writeFileSync(temporaryPath, output, 'utf8');
    fs.renameSync(temporaryPath, outputPath);
    console.log(`${INDEX_PATH}: generated (${catalog.entries.length} entries)`);
} finally {
    if (fs.existsSync(temporaryPath)) fs.rmSync(temporaryPath);
}
