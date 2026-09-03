import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { generateFeatureIndex, markdownText, validateCatalog } from './feature-catalog-lib.mjs';

function fixture() {
    const root = fs.mkdtempSync(path.join(os.tmpdir(), 'taurus-feature-catalog-'));
    const files = {
        'docs/example.md': '# Example\n\n## Stato del documento\n\nID catalogo: `example`.\nLo stato corrente è pubblicato nel [Catalogo funzionalità](features.md).\n',
        'taurus-be/src/main/java/Example.java': 'class Example {}\n',
        'taurus-be/src/test/java/ExampleTest.java': 'class ExampleTest {}\n',
        'taurus-be/src/main/resources/config/liquibase/changelog/example.xml': '<databaseChangeLog/>\n',
        'taurus-be/src/main/resources/config/liquibase/master.xml': '<databaseChangeLog/>\n',
        'taurus-be/src/main/resources/config/liquibase/tenant-master.xml': '<databaseChangeLog><include file="config/liquibase/changelog/example.xml"/></databaseChangeLog>\n',
    };
    for (const [name, content] of Object.entries(files)) {
        const target = path.join(root, ...name.split('/'));
        fs.mkdirSync(path.dirname(target), { recursive: true });
        fs.writeFileSync(target, content);
    }
    return root;
}

function entry(overrides = {}) {
    return {
        id: 'example',
        title: 'Example',
        kind: 'feature',
        designStatus: 'approved',
        deliveryStatus: 'implemented',
        spec: 'docs/example.md',
        modules: ['taurus-be'],
        evidence: {
            implementation: ['taurus-be/src/main/java/Example.java'],
            migrations: ['taurus-be/src/main/resources/config/liquibase/changelog/example.xml'],
            tests: ['taurus-be/src/test/java/ExampleTest.java'],
        },
        release: null,
        lastVerifiedOn: '2026-09-04',
        notes: 'Verified offline by Liquibase.',
        ...overrides,
    };
}

function validate(root, entries = [entry()]) {
    return validateCatalog({ schemaVersion: 1, entries }, root);
}

test('accepts a minimal implemented catalog', t => {
    const root = fixture();
    t.after(() => fs.rmSync(root, { recursive: true, force: true }));
    assert.deepEqual(validate(root), []);
});

test('reports duplicate and malformed IDs and deterministic ordering', t => {
    const root = fixture();
    t.after(() => fs.rmSync(root, { recursive: true, force: true }));
    const errors = validate(root, [entry({ id: 'z-example' }), entry({ id: 'Bad ID' }), entry({ id: 'z-example' })]);
    assert(errors.some(value => value.includes('must be kebab case')));
    assert(errors.some(value => value.includes('duplicates')));
    assert(errors.some(value => value.includes('must be sorted')));
});

test('reports unknown states', t => {
    const root = fixture();
    t.after(() => fs.rmSync(root, { recursive: true, force: true }));
    assert(validate(root, [entry({ deliveryStatus: 'done' })]).some(value => value.includes('not an allowed status')));
});

test('reports missing and unsafe paths', t => {
    const root = fixture();
    t.after(() => fs.rmSync(root, { recursive: true, force: true }));
    const unsafe = entry({ spec: '../secret.md' });
    unsafe.evidence = { implementation: ['taurus-be/missing.java'], migrations: [], tests: [] };
    const errors = validate(root, [unsafe]);
    assert(errors.some(value => value.includes('safe repository-relative path')));
    assert(errors.some(value => value.includes('does not exist')));
});

test('enforces evidence for in-progress and implemented entries', t => {
    const root = fixture();
    t.after(() => fs.rmSync(root, { recursive: true, force: true }));
    const empty = { implementation: [], migrations: [], tests: [] };
    assert.deepEqual(validate(root, [entry({ deliveryStatus: 'not-planned', evidence: empty })]), []);
    assert(validate(root, [entry({ deliveryStatus: 'in-progress', evidence: empty })]).some(value => value.includes('in-progress requires')));
    assert(validate(root, [entry({ evidence: empty })]).some(value => value.includes('implemented requires')));
});

test('validates release version and date', t => {
    const root = fixture();
    t.after(() => fs.rmSync(root, { recursive: true, force: true }));
    const errors = validate(root, [entry({ deliveryStatus: 'released', release: { version: '1.2', tag: 'v1.2.0', date: '2026-02-30' } })]);
    assert(errors.some(value => value.includes('vMAJOR.MINOR.PATCH')));
    assert(errors.some(value => value.includes('must be identical')));
    assert(errors.some(value => value.includes('real ISO date')));
});

test('validates replacement targets and cycles', t => {
    const root = fixture();
    t.after(() => fs.rmSync(root, { recursive: true, force: true }));
    const entries = [
        entry({ id: 'a', designStatus: 'superseded', supersededBy: 'b' }),
        entry({ id: 'b', designStatus: 'superseded', supersededBy: 'a' }),
    ];
    assert(validate(root, entries).some(value => value.includes('cycle')));
    assert(validate(root, [entry({ designStatus: 'superseded', supersededBy: 'missing' })]).some(value => value.includes('existing ID')));
});

test('requires migrations to be included by a master changelog', t => {
    const root = fixture();
    t.after(() => fs.rmSync(root, { recursive: true, force: true }));
    fs.writeFileSync(path.join(root, 'taurus-be/src/main/resources/config/liquibase/tenant-master.xml'), '<databaseChangeLog/>');
    assert(validate(root).some(value => value.includes('not included')));
});

test('requires an integration test or migration motivation', t => {
    const root = fixture();
    t.after(() => fs.rmSync(root, { recursive: true, force: true }));
    const errors = validate(root, [entry({ notes: undefined })]);
    assert(errors.some(value => value.includes('integration test')));
    assert.deepEqual(validate(root, [entry({ notes: 'Verified offline by Liquibase.' })]), []);
});

test('requires every managed document to be cataloged and linked back', t => {
    const root = fixture();
    t.after(() => fs.rmSync(root, { recursive: true, force: true }));
    fs.writeFileSync(path.join(root, 'docs/extra.md'), '# Extra\n');
    const errors = validate(root, [entry({ notes: 'Verified offline by Liquibase.' })]);
    assert(errors.some(value => value.includes('is not represented in the catalog')));
    fs.rmSync(path.join(root, 'docs/extra.md'));
    fs.writeFileSync(path.join(root, 'docs/example.md'), '# Missing status\n');
    assert(validate(root, [entry({ notes: 'Verified offline by Liquibase.' })]).some(value => value.includes('must declare catalog ID')));
});

test('escapes Markdown and generates deterministic output', t => {
    const root = fixture();
    t.after(() => fs.rmSync(root, { recursive: true, force: true }));
    const catalog = { schemaVersion: 1, entries: [entry({ title: '<b>A | [B]</b>', notes: '<script>x</script>' })] };
    const first = generateFeatureIndex(catalog);
    assert.equal(first, generateFeatureIndex(catalog));
    assert(first.includes('&lt;b&gt;A \\| \\[B\\]&lt;/b&gt;'));
    assert(!first.includes('<script>'));
    assert.equal(markdownText('a\nb'), 'a b');
});
