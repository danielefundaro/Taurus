import { Component, ElementRef, Input, OnChanges, Renderer2, SecurityContext, ViewChild, ViewEncapsulation } from '@angular/core';
import { DomSanitizer } from '@angular/platform-browser';

/** Identificativi dei titoli scritti dal server: lettere, cifre, trattini e trattini bassi. */
const HEADING_ID = /^[\p{L}\p{M}\p{N}_-]{1,128}$/u;
const HEADINGS = 'h2, h3, h4';

/** Il frammento di un indirizzo, senza la codifica; vuoto se la codifica non è valida. */
const decoded = (fragment: string): string => {
    try {
        return decodeURIComponent(fragment);
    } catch {
        return '';
    }
};

/**
 * Testo di un documento legale, già convertito e ripulito dal server: la pagina pubblica, la pagina di accettazione e
 * l'anteprima di «Documenti legali» lo mostrano allo stesso modo.
 *
 * Il testo passa comunque dalla pulizia di Angular, la terza barriera, che però toglie gli `id` dei titoli: il
 * componente li rimette, nello stesso ordine, se hanno la forma che produce il server. Così un collegamento a una
 * sezione, dentro o fuori dal testo, porta al titolo giusto.
 */
@Component({
    selector: 'app-legal-text',
    standalone: true,
    template: '<div #container class="legal-text" (click)="follow($event)"></div>',
    styleUrl: './legal-text.component.scss',
    encapsulation: ViewEncapsulation.None
})
export class LegalTextComponent implements OnChanges {
    @Input() html?: string | null;
    /** Nella pagina pubblica: porta alla sezione indicata nell'indirizzo e la aggiorna a ogni collegamento interno. */
    @Input() followFragment = false;

    @ViewChild('container', { static: true }) private readonly container!: ElementRef<HTMLElement>;

    constructor(
        private readonly sanitizer: DomSanitizer,
        private readonly renderer: Renderer2
    ) {}

    ngOnChanges(): void {
        // DOMParser builds an inert document: nothing in it runs or loads. The ids are set aside before the cleaning,
        // which would drop them, and given back afterwards.
        const parsed = new DOMParser().parseFromString(this.html ?? '', 'text/html');
        const ids = Array.from(parsed.querySelectorAll(HEADINGS), (heading) => {
            const id = heading.id;
            heading.removeAttribute('id');
            return id;
        });
        const element = this.container.nativeElement;
        this.renderer.setProperty(element, 'innerHTML', this.sanitizer.sanitize(SecurityContext.HTML, parsed.body.innerHTML) ?? '');
        element.querySelectorAll(HEADINGS).forEach((heading, index) => {
            const id = ids[index];
            if (id && HEADING_ID.test(id)) heading.id = id;
        });
        if (this.followFragment) {
            const fragment = decoded(globalThis.location?.hash?.slice(1) ?? '');
            if (fragment) setTimeout(() => this.heading(fragment)?.scrollIntoView());
        }
    }

    /** Un collegamento a una sezione del testo scorre fino al titolo, senza uscire dalla pagina. */
    protected follow(event: MouseEvent): void {
        const link = (event.target as Element | null)?.closest('a');
        const href = link?.getAttribute('href');
        if (!href?.startsWith('#')) return;
        event.preventDefault();
        const id = decoded(href.slice(1));
        const heading = this.heading(id);
        if (!heading) return;
        heading.scrollIntoView({ behavior: 'smooth', block: 'start' });
        if (this.followFragment) history.replaceState(history.state, '', `#${encodeURIComponent(id)}`);
    }

    private heading(id: string): HTMLElement | null {
        return HEADING_ID.test(id) ? this.container.nativeElement.querySelector<HTMLElement>(`[id="${id}"]`) : null;
    }
}
