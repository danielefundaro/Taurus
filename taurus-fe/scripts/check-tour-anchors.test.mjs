import assert from 'node:assert/strict';
import { test } from 'node:test';
import { catalogAnchors, declaredAnchors, missingAnchors } from './check-tour-anchors.mjs';

test('reads the anchors and the mobile anchors of the catalog', () => {
    const source = `
        { id: 'admin.users', anchors: ['users.create', 'users.header'], mobileAnchors: ['users.header'] },
        { id: 'common.help', anchors: ["topbar.help"] },
        { id: 'finance.events' }`;

    assert.deepEqual([...catalogAnchors(source)].sort(), ['topbar.help', 'users.create', 'users.header']);
});

test('reads the anchors of templates and of the menu model', () => {
    assert.deepEqual([...declaredAnchors('<app-page-header appTourAnchor="users.header" /><div [appTourAnchor]="\'users.create\'"></div>', 'users.component.html')], ['users.header', 'users.create']);
    assert.deepEqual([...declaredAnchors(`<div [appTourAnchor]="personal ? 'inventory.assignments' : 'inventory.items'"></div>`, 'inventory.component.html')], ['inventory.assignments', 'inventory.items']);
    assert.deepEqual([...declaredAnchors("{ label: 'Consegne', tourAnchor: 'layout.menu.admin' }", 'menu.component.ts')], ['layout.menu.admin']);
    assert.deepEqual([...declaredAnchors('appTourAnchor="ignored.inTypeScript"', 'menu.component.ts')], []);
});

test('reports an anchor cited by the catalog and missing from the interface', () => {
    assert.deepEqual(missingAnchors(new Set(['users.header', 'calendar.header']), new Set(['users.header'])), ['calendar.header']);
    assert.deepEqual(missingAnchors(new Set(['users.header']), new Set(['users.header', 'unused.anchor'])), []);
});
