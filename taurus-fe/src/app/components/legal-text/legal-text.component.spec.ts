import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LegalTextComponent } from './legal-text.component';

describe('LegalTextComponent', () => {
    let fixture: ComponentFixture<LegalTextComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({ imports: [LegalTextComponent] }).compileComponents();
        fixture = TestBed.createComponent(LegalTextComponent);
    });

    const show = (html: string, followFragment = false): HTMLElement => {
        fixture.componentRef.setInput('html', html);
        fixture.componentRef.setInput('followFragment', followFragment);
        fixture.detectChanges();
        return fixture.nativeElement.querySelector('.legal-text');
    };

    it('shows the text of the server with the ids of its headings', () => {
        const text = show('<h2 id="1-accesso">1. Accesso</h2>\n<p>Testo <strong>importante</strong>.</p><h3 id="finalità">Finalità</h3>');

        expect(text.querySelector('h2')?.id).toBe('1-accesso');
        expect(text.querySelector('h3')?.id).toBe('finalità');
        expect(text.querySelector('strong')?.textContent).toBe('importante');
    });

    it('cleans the text once more and gives back only the ids of the expected form', () => {
        const text = show('<h2 id="x&quot; onclick">Titolo</h2><p onclick="alert(1)">Testo</p><a href="javascript:alert(1)">link</a><script>alert(1)</script><img src="x" onerror="alert(1)">');

        expect(text.querySelector('h2')?.id).toBe('');
        expect(text.querySelector('p')?.hasAttribute('onclick')).toBeFalse();
        expect(text.querySelector('a')?.getAttribute('href')).toContain('unsafe:');
        expect(text.querySelector('script')).toBeNull();
        expect(text.querySelector('img')?.hasAttribute('onerror')).toBeFalse();
    });

    it('scrolls to a section of the text without leaving the page', () => {
        const text = show('<p><a href="#2-dati">sezione</a></p><h2 id="2-dati">2. Dati</h2>');
        const heading = text.querySelector('h2') as HTMLElement;
        spyOn(heading, 'scrollIntoView');
        const replaceState = spyOn(history, 'replaceState');

        const click = new MouseEvent('click', { bubbles: true, cancelable: true });
        text.querySelector('a')?.dispatchEvent(click);

        expect(click.defaultPrevented).toBeTrue();
        expect(heading.scrollIntoView).toHaveBeenCalled();
        expect(replaceState).not.toHaveBeenCalled();
    });

    it('updates the address with the section on the public page', () => {
        const text = show('<p><a href="#2-dati">sezione</a></p><h2 id="2-dati">2. Dati</h2>', true);
        spyOn(text.querySelector('h2') as HTMLElement, 'scrollIntoView');
        const replaceState = spyOn(history, 'replaceState');

        text.querySelector('a')?.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true }));

        expect(replaceState).toHaveBeenCalledWith(history.state, '', '#2-dati');
    });

    it('leaves the other links to the browser', () => {
        const text = show('<p><a href="https://example.org" target="_blank" rel="noopener noreferrer">sito</a></p>');
        const link = text.querySelector('a') as HTMLAnchorElement;
        link.addEventListener('click', (event) => event.preventDefault());
        const click = new MouseEvent('click', { bubbles: true, cancelable: true });
        const follow = spyOn(Element.prototype, 'scrollIntoView');

        link.dispatchEvent(click);

        expect(link.target).toBe('_blank');
        expect(follow).not.toHaveBeenCalled();
    });

    it('shows nothing for an empty text', () => {
        expect(show('').innerHTML).toBe('');
    });
});
