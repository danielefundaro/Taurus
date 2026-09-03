import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export const CATALOG_PATH = 'docs/features.json';
export const INDEX_PATH = 'docs/features.md';
export const SUPPORTED_SCHEMA_VERSION = 1;
export const KINDS = new Set(['feature', 'platform', 'migration', 'standard']);
export const DESIGN_STATUSES = new Set(['draft', 'approved', 'superseded', 'archived']);
export const DELIVERY_STATUSES = new Set([
    'not-planned',
    'planned',
    'in-progress',
    'implemented',
    'released',
    'deprecated',
    'removed',
]);
export const MODULES = new Set(['taurus-be', 'taurus-fe', 'keycloak-authenticator', 'taurus-info', 'repository']);

const MODULE_LABELS = new Map([
    ['taurus-be', 'BE'],
    ['taurus-fe', 'FE'],
    ['keycloak-authenticator', 'Keycloak'],
    ['taurus-info', 'Info'],
    ['repository', 'Repository'],
]);

const EVIDENCE_GROUPS = ['implementation', 'migrations', 'tests'];
const SEMVER = /^v(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)$/;
const ID_PATTERN = /^[a-z0-9]+(?:-[a-z0-9]+)*$/;
const DATE_PATTERN = /^\d{4}-\d{2}-\d{2}$/;

export const defaultRepositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..');

function issue(location, message) {
    return `${location}: ${message}`;
}

function isRecord(value) {
    return value !== null && typeof value === 'object' && !Array.isArray(value);
}

function validDate(value) {
    if (typeof value !== 'string' || !DATE_PATTERN.test(value)) return false;
    const [year, month, day] = value.split('-').map(Number);
    const date = new Date(Date.UTC(year, month - 1, day));
    return date.getUTCFullYear() === year && date.getUTCMonth() === month - 1 && date.getUTCDate() === day;
}

export function isSafeRepositoryPath(value) {
    if (typeof value !== 'string' || value.length === 0 || value.includes('\\')) return false;
    if (value.startsWith('/') || /^[A-Za-z]:/.test(value) || value.startsWith('//')) return false;
    if (/^[a-z][a-z0-9+.-]*:/i.test(value)) return false;
    const segments = value.split('/');
    if (segments.some(segment => segment === '' || segment === '.' || segment === '..')) return false;
    return !segments.some(segment => /^(?:\.env|credentials?|secrets?)(?:\.|$)/i.test(segment) || /\.(?:jks|p12|pfx|pem|key)$/i.test(segment));
}

function moduleForPath(repositoryPath) {
    const first = repositoryPath.split('/')[0];
    return MODULES.has(first) && first !== 'repository' ? first : 'repository';
}

function validatePath(value, location, repositoryRoot, errors, fileCache, { expectedMarkdown = false } = {}) {
    if (!isSafeRepositoryPath(value)) {
        errors.push(issue(location, `${JSON.stringify(value)} must be a safe repository-relative path using /`));
        return false;
    }
    if (expectedMarkdown && !value.endsWith('.md')) {
        errors.push(issue(location, `${JSON.stringify(value)} must point to a Markdown document`));
    }

    const absolute = path.resolve(repositoryRoot, ...value.split('/'));
    const relative = path.relative(repositoryRoot, absolute);
    if (relative.startsWith('..') || path.isAbsolute(relative)) {
        errors.push(issue(location, `${JSON.stringify(value)} resolves outside the repository`));
        return false;
    }

    let exists = fileCache.get(absolute);
    if (exists === undefined) {
        try {
            exists = fs.statSync(absolute).isFile();
        } catch {
            exists = false;
        }
        fileCache.set(absolute, exists);
    }
    if (!exists) errors.push(issue(location, `${JSON.stringify(value)} does not exist as a file`));
    return exists;
}

function validateStringArray(value, location, errors) {
    if (!Array.isArray(value)) {
        errors.push(issue(location, 'must be an array'));
        return [];
    }
    const strings = [];
    const seen = new Set();
    value.forEach((item, index) => {
        if (typeof item !== 'string' || item.length === 0) {
            errors.push(issue(`${location}[${index}]`, 'must be a non-empty string'));
            return;
        }
        if (seen.has(item)) errors.push(issue(`${location}[${index}]`, `${JSON.stringify(item)} is duplicated`));
        seen.add(item);
        strings.push(item);
    });
    return strings;
}

function readIncludedMigrations(repositoryRoot, fileCache) {
    const result = new Set();
    for (const master of [
        'taurus-be/src/main/resources/config/liquibase/master.xml',
        'taurus-be/src/main/resources/config/liquibase/tenant-master.xml',
    ]) {
        const absolute = path.resolve(repositoryRoot, ...master.split('/'));
        let content = fileCache.get(`content:${absolute}`);
        if (content === undefined) {
            try {
                content = fs.readFileSync(absolute, 'utf8');
            } catch {
                content = '';
            }
            fileCache.set(`content:${absolute}`, content);
        }
        for (const match of content.matchAll(/<include\s+[^>]*file=["']([^"']+)["'][^>]*\/?\s*>/g)) {
            result.add(`taurus-be/src/main/resources/${match[1].replaceAll('\\', '/')}`);
        }
    }
    return result;
}

export function loadCatalog(repositoryRoot = defaultRepositoryRoot) {
    const absolute = path.resolve(repositoryRoot, ...CATALOG_PATH.split('/'));
    const source = fs.readFileSync(absolute, 'utf8').replace(/^\uFEFF/, '');
    return JSON.parse(source);
}

export function validateCatalog(catalog, repositoryRoot = defaultRepositoryRoot) {
    const errors = [];
    const fileCache = new Map();
    if (!isRecord(catalog)) return [issue('', 'catalog must be a JSON object')];
    if (catalog.schemaVersion !== SUPPORTED_SCHEMA_VERSION) {
        errors.push(issue('schemaVersion', `must be ${SUPPORTED_SCHEMA_VERSION}`));
    }
    if (!Array.isArray(catalog.entries)) {
        errors.push(issue('entries', 'must be an array'));
        return errors;
    }

    const ids = new Map();
    let previousId = null;
    const migrations = readIncludedMigrations(repositoryRoot, fileCache);

    catalog.entries.forEach((entry, entryIndex) => {
        const base = `entries[${entryIndex}]`;
        if (!isRecord(entry)) {
            errors.push(issue(base, 'must be an object'));
            return;
        }

        if (typeof entry.id !== 'string' || !ID_PATTERN.test(entry.id)) {
            errors.push(issue(`${base}.id`, `${JSON.stringify(entry.id)} must be kebab case`));
        } else {
            if (ids.has(entry.id)) errors.push(issue(`${base}.id`, `${JSON.stringify(entry.id)} duplicates entries[${ids.get(entry.id)}].id`));
            ids.set(entry.id, entryIndex);
            if (previousId !== null && previousId >= entry.id) {
                errors.push(issue(`${base}.id`, `${JSON.stringify(entry.id)} must be sorted after ${JSON.stringify(previousId)}`));
            }
            previousId = entry.id;
        }

        for (const field of ['title', 'spec']) {
            if (typeof entry[field] !== 'string' || entry[field].trim() === '') {
                errors.push(issue(`${base}.${field}`, 'must be a non-empty string'));
            }
        }
        if (!KINDS.has(entry.kind)) errors.push(issue(`${base}.kind`, `${JSON.stringify(entry.kind)} is not an allowed kind`));
        if (!DESIGN_STATUSES.has(entry.designStatus)) errors.push(issue(`${base}.designStatus`, `${JSON.stringify(entry.designStatus)} is not an allowed status`));
        if (!DELIVERY_STATUSES.has(entry.deliveryStatus)) errors.push(issue(`${base}.deliveryStatus`, `${JSON.stringify(entry.deliveryStatus)} is not an allowed status`));
        if (!validDate(entry.lastVerifiedOn)) errors.push(issue(`${base}.lastVerifiedOn`, `${JSON.stringify(entry.lastVerifiedOn)} must be a real ISO date`));

        if (typeof entry.spec === 'string') {
            const specExists = validatePath(entry.spec, `${base}.spec`, repositoryRoot, errors, fileCache, { expectedMarkdown: true });
            if (specExists) {
                const absolute = path.resolve(repositoryRoot, ...entry.spec.split('/'));
                let content = fileCache.get(`content:${absolute}`);
                if (content === undefined) {
                    content = fs.readFileSync(absolute, 'utf8');
                    fileCache.set(`content:${absolute}`, content);
                }
                if (!content.includes(`ID catalogo: \`${entry.id}\`.`)) {
                    errors.push(issue(`${base}.spec`, `${JSON.stringify(entry.spec)} must declare catalog ID ${JSON.stringify(entry.id)} in its status section`));
                }
                if (!content.includes('[Catalogo funzionalità](features.md)')) {
                    errors.push(issue(`${base}.spec`, `${JSON.stringify(entry.spec)} must link to the generated feature catalog`));
                }
            }
        }
        const relatedDocs = entry.relatedDocs === undefined ? [] : validateStringArray(entry.relatedDocs, `${base}.relatedDocs`, errors);
        relatedDocs.forEach((document, index) => {
            if (document === entry.spec) errors.push(issue(`${base}.relatedDocs[${index}]`, 'must not duplicate spec'));
            validatePath(document, `${base}.relatedDocs[${index}]`, repositoryRoot, errors, fileCache, { expectedMarkdown: true });
        });

        const modules = validateStringArray(entry.modules, `${base}.modules`, errors);
        if (modules.length === 0) errors.push(issue(`${base}.modules`, 'must contain at least one module'));
        modules.forEach((module, index) => {
            if (!MODULES.has(module)) errors.push(issue(`${base}.modules[${index}]`, `${JSON.stringify(module)} is not an allowed module`));
        });

        const evidence = isRecord(entry.evidence) ? entry.evidence : null;
        if (!evidence) errors.push(issue(`${base}.evidence`, 'must be an object'));
        const groups = {};
        for (const group of EVIDENCE_GROUPS) {
            const values = evidence ? validateStringArray(evidence[group], `${base}.evidence.${group}`, errors) : [];
            groups[group] = values;
            values.forEach((repositoryPath, index) => {
                const location = `${base}.evidence.${group}[${index}]`;
                validatePath(repositoryPath, location, repositoryRoot, errors, fileCache);
                const pathModule = isSafeRepositoryPath(repositoryPath) ? moduleForPath(repositoryPath) : null;
                if (pathModule && !modules.includes(pathModule)) {
                    errors.push(issue(location, `${JSON.stringify(repositoryPath)} belongs to module ${JSON.stringify(pathModule)}, which is not listed in modules`));
                }
                if (group === 'migrations' && !migrations.has(repositoryPath)) {
                    errors.push(issue(location, `${JSON.stringify(repositoryPath)} is not included by the Liquibase master changelogs`));
                }
                if (group === 'migrations' && !modules.includes('taurus-be')) {
                    errors.push(issue(location, 'a Liquibase migration requires module "taurus-be"'));
                }
            });
        }

        if (entry.deliveryStatus === 'in-progress' && groups.implementation.length + groups.migrations.length === 0) {
            errors.push(issue(`${base}.evidence`, 'in-progress requires implementation or migration evidence'));
        }
        if (['implemented', 'released'].includes(entry.deliveryStatus)) {
            if (groups.implementation.length === 0) errors.push(issue(`${base}.evidence.implementation`, `${entry.deliveryStatus} requires implementation evidence`));
            if (groups.tests.length === 0) errors.push(issue(`${base}.evidence.tests`, `${entry.deliveryStatus} requires test evidence`));
        }
        if (entry.kind === 'migration' && ['implemented', 'released'].includes(entry.deliveryStatus) && groups.migrations.length === 0) {
            errors.push(issue(`${base}.evidence.migrations`, 'an implemented migration requires migration evidence'));
        }
        if (groups.migrations.length > 0 && !groups.tests.some(testPath => /IT\.java$/.test(testPath)) && !(typeof entry.notes === 'string' && entry.notes.trim())) {
            errors.push(issue(`${base}.evidence.migrations`, 'migration evidence requires an integration test (*IT.java) or a short motivation in notes'));
        }

        if (entry.deliveryStatus === 'released') {
            if (!isRecord(entry.release)) {
                errors.push(issue(`${base}.release`, 'released requires version, date and tag'));
            }
        }
        if (entry.release !== null && entry.release !== undefined) {
            if (!isRecord(entry.release)) {
                errors.push(issue(`${base}.release`, 'must be null or an object'));
            } else {
                if (!SEMVER.test(entry.release.version ?? '')) errors.push(issue(`${base}.release.version`, `${JSON.stringify(entry.release.version)} must match vMAJOR.MINOR.PATCH`));
                if (!SEMVER.test(entry.release.tag ?? '')) errors.push(issue(`${base}.release.tag`, `${JSON.stringify(entry.release.tag)} must match vMAJOR.MINOR.PATCH`));
                if (entry.release.version !== entry.release.tag) errors.push(issue(`${base}.release`, 'version and tag must be identical'));
                if (!validDate(entry.release.date)) errors.push(issue(`${base}.release.date`, `${JSON.stringify(entry.release.date)} must be a real ISO date`));
            }
        }
        if (entry.deliveryStatus !== 'released' && entry.release !== null && entry.release !== undefined) {
            errors.push(issue(`${base}.release`, 'must be null until deliveryStatus is released'));
        }

        if (entry.designStatus === 'superseded' && (typeof entry.supersededBy !== 'string' || entry.supersededBy.length === 0)) {
            errors.push(issue(`${base}.supersededBy`, 'superseded design requires a replacement ID'));
        }
        if (entry.supersededBy !== undefined && typeof entry.supersededBy !== 'string') {
            errors.push(issue(`${base}.supersededBy`, 'must be a feature ID'));
        }
        if (entry.notes !== undefined && (typeof entry.notes !== 'string' || entry.notes.trim() === '')) {
            errors.push(issue(`${base}.notes`, 'must be a non-empty string when present'));
        }
        if (typeof entry.notes === 'string' && /https?:\/\/[^\s/@]+:[^\s/@]+@/i.test(entry.notes)) {
            errors.push(issue(`${base}.notes`, 'must not contain a URL with credentials'));
        }
        if (typeof entry.notes === 'string' && /http:\/\//i.test(entry.notes)) {
            errors.push(issue(`${base}.notes`, 'external links must use HTTPS'));
        }
    });

    for (const [id, index] of ids) {
        const replacement = catalog.entries[index].supersededBy;
        if (typeof replacement === 'string' && !ids.has(replacement)) {
            errors.push(issue(`entries[${index}].supersededBy`, `${JSON.stringify(replacement)} does not reference an existing ID`));
        }
        if (replacement === id) errors.push(issue(`entries[${index}].supersededBy`, 'must not reference the same entry'));
    }

    const catalogedSpecs = new Set(catalog.entries.map(entry => entry.spec));
    const docsDirectory = path.resolve(repositoryRoot, 'docs');
    try {
        for (const directoryEntry of fs.readdirSync(docsDirectory, { withFileTypes: true })) {
            if (!directoryEntry.isFile() || !directoryEntry.name.endsWith('.md')) continue;
            if (['features.md', 'llms-full.md'].includes(directoryEntry.name)) continue;
            const repositoryPath = `docs/${directoryEntry.name}`;
            if (!catalogedSpecs.has(repositoryPath)) errors.push(issue('entries', `${JSON.stringify(repositoryPath)} is not represented in the catalog`));
        }
    } catch {
        errors.push(issue('entries', 'docs directory cannot be read'));
    }

    const visited = new Set();
    const active = new Set();
    function visit(id) {
        if (active.has(id)) return true;
        if (visited.has(id)) return false;
        visited.add(id);
        active.add(id);
        const index = ids.get(id);
        const next = index === undefined ? undefined : catalog.entries[index].supersededBy;
        if (typeof next === 'string' && ids.has(next) && visit(next)) return true;
        active.delete(id);
        return false;
    }
    for (const id of ids.keys()) {
        active.clear();
        if (visit(id)) {
            errors.push(issue('entries', `supersededBy relationships contain a cycle involving ${JSON.stringify(id)}`));
            break;
        }
    }

    return errors;
}

export function markdownText(value) {
    return String(value)
        .replaceAll('&', '&amp;')
        .replaceAll('<', '&lt;')
        .replaceAll('>', '&gt;')
        .replaceAll('|', '\\|')
        .replaceAll('[', '\\[')
        .replaceAll(']', '\\]')
        .replaceAll('\r', ' ')
        .replaceAll('\n', ' ');
}

function detailsAnchor(id) {
    return `${id}-evidenze`;
}

function evidenceCell(entry, group) {
    const count = entry.evidence[group].length;
    return count === 0 ? '0' : `[${count}](#${detailsAnchor(entry.id)})`;
}

function linkFromDocs(repositoryPath) {
    return repositoryPath.startsWith('docs/') ? repositoryPath.slice('docs/'.length) : `../${repositoryPath}`;
}

export function generateFeatureIndex(catalog) {
    const lines = [
        '<!-- File generato: non modificare manualmente. -->',
        '# Catalogo funzionalità Taurus',
        '',
        'Fonte: [`docs/features.json`](features.json). Rigenerare con `node scripts/docs/generate-feature-index.mjs`.',
        '',
        '| ID | Funzionalità | Tipo | Progettazione | Consegna | Moduli | Migrazioni | Test | Release | Verificata |',
        '| --- | --- | --- | --- | --- | --- | --- | ---: | --- | --- |',
    ];

    for (const entry of catalog.entries) {
        const release = entry.release ? `${markdownText(entry.release.version)} (${markdownText(entry.release.date)})` : 'non rilasciata';
        const modules = entry.modules.map(module => MODULE_LABELS.get(module) ?? markdownText(module)).join(', ');
        lines.push(
            `| \`${entry.id}\` | [${markdownText(entry.title)}](${linkFromDocs(entry.spec)}) | ${entry.kind} | ${entry.designStatus} | ${entry.deliveryStatus} | ${modules} | ${evidenceCell(entry, 'migrations')} | ${evidenceCell(entry, 'tests')} | ${release} | ${entry.lastVerifiedOn} |`,
        );
    }

    lines.push('', '## Evidenze', '');
    for (const entry of catalog.entries) {
        lines.push(`<a id="${detailsAnchor(entry.id)}"></a>`, `### ${markdownText(entry.title)}`, '');
        for (const [group, label] of [
            ['implementation', 'Implementazione'],
            ['migrations', 'Migrazioni'],
            ['tests', 'Test'],
        ]) {
            const values = entry.evidence[group];
            lines.push(`- ${label}: ${values.length === 0 ? 'nessuna' : values.map(value => `[\`${markdownText(value)}\`](${linkFromDocs(value)})`).join(', ')}`);
        }
        if (entry.supersededBy) lines.push(`- Sostituita da: \`${markdownText(entry.supersededBy)}\``);
        if (entry.notes) lines.push(`- Note: ${markdownText(entry.notes)}`);
        lines.push('');
    }
    return `${lines.join('\n')}\n`;
}

export function validateReleaseTag(tag) {
    return SEMVER.test(tag);
}
