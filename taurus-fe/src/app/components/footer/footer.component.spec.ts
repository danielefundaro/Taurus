import { TestBed } from '@angular/core/testing';
import { FooterComponent } from './footer.component';

describe('FooterComponent', () => {
    it('links the legal documents in a new tab, where the page opens without touching the session', () => {
        TestBed.configureTestingModule({ imports: [FooterComponent] });
        const fixture = TestBed.createComponent(FooterComponent);
        fixture.detectChanges();

        const links = Array.from(fixture.nativeElement.querySelectorAll('a') as NodeListOf<HTMLAnchorElement>, (link) => [link.textContent?.trim(), link.getAttribute('href'), link.target]);
        expect(links).toEqual([
            ['Termini di utilizzo', '/legal/terms', '_blank'],
            ['Informativa privacy', '/legal/privacy', '_blank']
        ]);
    });
});
