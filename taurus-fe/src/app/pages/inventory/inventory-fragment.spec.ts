import { fakeAsync, tick } from '@angular/core/testing';
import { focusInventoryFragment } from './inventory-fragment';

describe('focusInventoryFragment', () => {
    let section: HTMLElement;

    beforeEach(() => {
        section = document.createElement('h2');
        section.id = 'segnalazioni-test';
        document.body.appendChild(section);
    });

    afterEach(() => section.remove());

    it('scrolls to the named section and focuses it once the page is drawn', fakeAsync(() => {
        spyOn(section, 'scrollIntoView');

        focusInventoryFragment('segnalazioni-test');
        expect(document.activeElement).not.toBe(section);
        tick();

        expect(section.getAttribute('tabindex')).toBe('-1');
        expect(section.scrollIntoView).toHaveBeenCalled();
        expect(document.activeElement).toBe(section);
    }));

    it('ignores a missing fragment or section', fakeAsync(() => {
        expect(() => {
            focusInventoryFragment(null);
            focusInventoryFragment('assente');
            tick();
        }).not.toThrow();
    }));
});
